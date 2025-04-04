// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


package com.starrocks.authentication;

import com.google.gson.annotations.SerializedName;
import com.starrocks.analysis.UserIdentity;
import com.starrocks.common.Config;
import com.starrocks.common.DdlException;
import com.starrocks.common.Pair;
import com.starrocks.mysql.privilege.AuthPlugin;
import com.starrocks.mysql.privilege.Password;
import com.starrocks.persist.metablock.SRMetaBlockEOFException;
import com.starrocks.persist.metablock.SRMetaBlockException;
import com.starrocks.persist.metablock.SRMetaBlockReader;
import com.starrocks.persist.metablock.SRMetaBlockWriter;
import com.starrocks.privilege.PrivilegeException;
import com.starrocks.privilege.PrivilegeManager;
import com.starrocks.privilege.UserPrivilegeCollection;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.sql.ast.AlterUserStmt;
import com.starrocks.sql.ast.CreateUserStmt;
import com.starrocks.sql.ast.DropUserStmt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AuthenticationManager {
    private static final Logger LOG = LogManager.getLogger(AuthenticationManager.class);
    private static final String DEFAULT_PLUGIN = PlainPasswordAuthenticationProvider.PLUGIN_NAME;

    public static final String ROOT_USER = "root";

    // core data structure
    // user identity -> all the authentication information
    // will be manually serialized one by one
    protected Map<UserIdentity, UserAuthenticationInfo> userToAuthenticationInfo = new TreeMap<>((o1, o2) -> {
        // make sure that ip > domain > %
        int compareHostScore = scoreUserIdentityHost(o1).compareTo(scoreUserIdentityHost(o2));
        if (compareHostScore != 0) {
            return compareHostScore;
        }
        // host type is the same, compare host
        int compareByHost = o1.getHost().compareTo(o2.getHost());
        if (compareByHost != 0) {
            return compareByHost;
        }
        // compare user name
        return o1.getQualifiedUser().compareTo(o2.getQualifiedUser());
    });

    // For legacy reason, user property are set by username instead of full user identity.
    @SerializedName(value = "m")
    private Map<String, UserProperty> userNameToProperty = new HashMap<>();

    // resolve hostname to ip
    private Map<String, Set<String>> hostnameToIpSet = new HashMap<>();
    private final ReentrantReadWriteLock hostnameToIpLock = new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // set by load() to distinguish brand-new environment with upgraded environment
    private boolean isLoaded = false;

    private void readLock() {
        lock.readLock().lock();
    }

    private void readUnlock() {
        lock.readLock().unlock();
    }

    private void writeLock() {
        lock.writeLock().lock();
    }

    private void writeUnlock() {
        lock.writeLock().unlock();
    }

    public AuthenticationManager() {
        // default plugin
        AuthenticationProviderFactory.installPlugin(
                PlainPasswordAuthenticationProvider.PLUGIN_NAME, new PlainPasswordAuthenticationProvider());
        AuthenticationProviderFactory.installPlugin(
                LDAPAuthenticationProvider.PLUGIN_NAME, new LDAPAuthenticationProvider());

        // default user
        UserAuthenticationInfo info = new UserAuthenticationInfo();
        try {
            info.setOrigUserHost(ROOT_USER, UserAuthenticationInfo.ANY_HOST);
        } catch (AuthenticationException e) {
            throw new RuntimeException("should not happened!", e);
        }
        info.setAuthPlugin(PlainPasswordAuthenticationProvider.PLUGIN_NAME);
        info.setPassword(new byte[0]);
        userToAuthenticationInfo.put(UserIdentity.ROOT, info);
        userNameToProperty.put(UserIdentity.ROOT.getQualifiedUser(), new UserProperty());
    }

    public boolean doesUserExist(UserIdentity userIdentity) {
        readLock();
        try {
            return userToAuthenticationInfo.containsKey(userIdentity);
        } finally {
            readUnlock();
        }
    }

    public long getMaxConn(String userName) {
        return userNameToProperty.get(userName).getMaxConn();
    }

    public String getDefaultPlugin() {
        return DEFAULT_PLUGIN;
    }

    /**
     * If someone log in from 10.1.1.1 with name "test_user", the matching UserIdentity can be sorted in the below order
     * 1. test_user@10.1.1.1
     * 2. test_user@["hostname"], in which "hostname" can be resolved to 10.1.1.1.
     * If multiple hostnames match the login ip, just return one randomly.
     * 3. test_user@%, as a fallback.
     */
    private Integer scoreUserIdentityHost(UserIdentity userIdentity) {
        // ip(1) > hostname(2) > %(3)
        if (userIdentity.isDomain()) {
            return 2;
        }
        if (userIdentity.getHost().equals(UserAuthenticationInfo.ANY_HOST)) {
            return 3;
        }
        return 1;
    }

    private boolean match(String remoteUser, String remoteHost, boolean isDomain, UserAuthenticationInfo info) {
        // quickly filter unmatched entries by username
        if (!info.matchUser(remoteUser)) {
            return false;
        }
        if (isDomain) {
            // check for resolved ips
            this.hostnameToIpLock.readLock().lock();
            try {
                Set<String> ipSet = hostnameToIpSet.get(info.getOrigHost());
                if (ipSet == null) {
                    return false;
                }
                return ipSet.contains(remoteHost);
            } finally {
                this.hostnameToIpLock.readLock().unlock();
            }
        } else {
            return info.matchHost(remoteHost);
        }
    }

    public UserIdentity checkPassword(String remoteUser, String remoteHost, byte[] remotePasswd, byte[] randomString) {
        Iterator<Map.Entry<UserIdentity, UserAuthenticationInfo>> it = userToAuthenticationInfo.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UserIdentity, UserAuthenticationInfo> entry = it.next();
            UserIdentity userIdentity = entry.getKey();
            UserAuthenticationInfo info = entry.getValue();
            if (match(remoteUser, remoteHost, userIdentity.isDomain(), info)) {
                try {
                    AuthenticationProvider provider = AuthenticationProviderFactory.create(info.getAuthPlugin());
                    provider.authenticate(remoteUser, remoteHost, remotePasswd, randomString, info);
                    return userIdentity;
                } catch (AuthenticationException e) {
                    LOG.debug("failed to authentication, ", e);
                }
                return null;  // authentication failed
            }
        }
        LOG.debug("cannot find user {}@{}", remoteUser, remoteHost);
        return null; // cannot find user
    }

    public UserIdentity checkPlainPassword(String remoteUser, String remoteHost, String remotePasswd) {
        return checkPassword(remoteUser, remoteHost,
                remotePasswd.getBytes(StandardCharsets.UTF_8), null);
    }

    public void checkPasswordReuse(UserIdentity user, String plainPassword) throws DdlException {
        if (Config.enable_password_reuse) {
            return;
        }
        if (checkPlainPassword(user.getQualifiedUser(), user.getHost(), plainPassword) != null) {
            throw new DdlException("password should not be the same as the previous one!");
        }
    }

    public void createUser(CreateUserStmt stmt) throws DdlException {
        UserIdentity userIdentity = stmt.getUserIdent();
        UserAuthenticationInfo info = stmt.getAuthenticationInfo();
        writeLock();
        try {
            updateUserNoLock(userIdentity, info, false);
            UserProperty userProperty = null;
            if (!userNameToProperty.containsKey(userIdentity.getQualifiedUser())) {
                userProperty = new UserProperty();
                userNameToProperty.put(userIdentity.getQualifiedUser(), userProperty);
            }
            GlobalStateMgr globalStateMgr = GlobalStateMgr.getCurrentState();
            PrivilegeManager privilegeManager = globalStateMgr.getPrivilegeManager();
            // init user privilege
            UserPrivilegeCollection collection = privilegeManager.onCreateUser(userIdentity);
            short pluginId = privilegeManager.getProviderPluginId();
            short pluginVersion = privilegeManager.getProviderPluginVersion();
            globalStateMgr.getEditLog().logCreateUser(
                    userIdentity, info, userProperty, collection, pluginId, pluginVersion);

        } catch (AuthenticationException | PrivilegeException e) {
            throw new DdlException("failed to create user " + userIdentity, e);
        } finally {
            writeUnlock();
        }
    }

    public void alterUser(AlterUserStmt stmt) throws DdlException {
        UserIdentity userIdentity = stmt.getUserIdent();
        UserAuthenticationInfo info = stmt.getAuthenticationInfo();
        updateUserWithAuthenticationInfo(userIdentity, info);
    }

    public void updateUserWithAuthenticationInfo(UserIdentity userIdentity,
                                                 UserAuthenticationInfo info) throws DdlException {
        writeLock();
        try {
            updateUserNoLock(userIdentity, info, true);
            GlobalStateMgr.getCurrentState().getEditLog().logAlterUser(userIdentity, info);
        } catch (AuthenticationException e) {
            throw new DdlException("failed to alter user " + userIdentity, e);
        } finally {
            writeUnlock();
        }
    }

    private void updateUserPropertyNoLock(String user, List<Pair<String, String>> properties) throws DdlException {
        UserProperty userProperty = userNameToProperty.getOrDefault(user, null);
        if (userProperty == null) {
            throw new DdlException("user '" + user + "' doesn't exist");
        }
        userProperty.update(properties);
    }

    public void updateUserProperty(String user, List<Pair<String, String>> properties) throws DdlException {
        try {
            writeLock();
            updateUserPropertyNoLock(user, properties);
            UserPropertyInfo propertyInfo = new UserPropertyInfo(user, properties);
            GlobalStateMgr.getCurrentState().getEditLog().logUpdateUserPropertyV2(propertyInfo);
            LOG.info("finished to update user '{}' with properties: {}", user, properties);
        } finally {
            writeUnlock();
        }
    }

    public void replayUpdateUserProperty(UserPropertyInfo info) throws DdlException {
        try {
            writeLock();
            updateUserPropertyNoLock(info.getUser(), info.getProperties());
        } finally {
            writeUnlock();
        }
    }

    public void replayAlterUser(UserIdentity userIdentity, UserAuthenticationInfo info) throws AuthenticationException {
        writeLock();
        try {
            updateUserNoLock(userIdentity, info, true);
        } finally {
            writeUnlock();
        }
    }

    public void dropUser(DropUserStmt stmt) throws DdlException {
        UserIdentity userIdentity = stmt.getUserIdent();
        writeLock();
        try {
            dropUserNoLock(userIdentity);
            // drop user privilege as well
            GlobalStateMgr.getCurrentState().getPrivilegeManager().onDropUser(userIdentity);
            GlobalStateMgr.getCurrentState().getEditLog().logDropUser(userIdentity);
        } finally {
            writeUnlock();
        }
    }

    public void replayDropUser(UserIdentity userIdentity) throws DdlException {
        writeLock();
        try {
            dropUserNoLock(userIdentity);
            // drop user privilege as well
            GlobalStateMgr.getCurrentState().getPrivilegeManager().onDropUser(userIdentity);
        } finally {
            writeUnlock();
        }
    }

    private void dropUserNoLock(UserIdentity userIdentity) {
        // 1. remove from userToAuthenticationInfo
        if (!userToAuthenticationInfo.containsKey(userIdentity)) {
            LOG.warn("cannot find user {}", userIdentity);
            return;
        }
        userToAuthenticationInfo.remove(userIdentity);
        LOG.info("user {} is dropped", userIdentity);
        // 2. remove from userNameToProperty
        String userName = userIdentity.getQualifiedUser();
        if (!hasUserNameNoLock(userName)) {
            LOG.info("user property for {} is dropped: {}", userName, userNameToProperty.get(userName));
            userNameToProperty.remove(userName);
        }
        // 3. TODO remove authentication
    }

    public void replayCreateUser(
            UserIdentity userIdentity,
            UserAuthenticationInfo info,
            UserProperty userProperty,
            UserPrivilegeCollection privilegeCollection,
            short pluginId,
            short pluginVersion)
            throws AuthenticationException, PrivilegeException {
        writeLock();
        try {
            info.analyze();
            updateUserNoLock(userIdentity, info, false);
            if (userProperty != null) {
                userNameToProperty.put(userIdentity.getQualifiedUser(), userProperty);
            }

            GlobalStateMgr globalStateMgr = GlobalStateMgr.getCurrentState();
            globalStateMgr.getPrivilegeManager().replayUpdateUserPrivilegeCollection(
                    userIdentity, privilegeCollection, pluginId, pluginVersion);
        } finally {
            writeUnlock();
        }
    }

    private void updateUserNoLock(
            UserIdentity userIdentity, UserAuthenticationInfo info, boolean shouldExists) throws AuthenticationException {
        if (userToAuthenticationInfo.containsKey(userIdentity)) {
            if (!shouldExists) {
                throw new AuthenticationException("user " + userIdentity.getQualifiedUser() + " already exists");
            }
        } else {
            if (shouldExists) {
                throw new AuthenticationException("failed to find user " + userIdentity.getQualifiedUser());
            }
        }
        userToAuthenticationInfo.put(userIdentity, info);
    }

    private boolean hasUserNameNoLock(String userName) {
        for (UserIdentity userIdentity : userToAuthenticationInfo.keySet()) {
            if (userIdentity.getQualifiedUser().equals(userName)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getAllHostnames() {
        readLock();
        try {
            Set<String> ret = new HashSet<>();
            for (UserIdentity userIdentity : userToAuthenticationInfo.keySet()) {
                if (userIdentity.isDomain()) {
                    ret.add(userIdentity.getHost());
                }
            }
            return ret;
        } finally {
            readUnlock();
        }
    }

    /**
     * called by DomainResolver to periodically update hostname -> ip set
     */
    public void setHostnameToIpSet(Map<String, Set<String>> hostnameToIpSet) {
        this.hostnameToIpLock.writeLock().lock();
        try {
            this.hostnameToIpSet = hostnameToIpSet;
        } finally {
            this.hostnameToIpLock.writeLock().unlock();
        }
    }

    /**
     * Use new image format by SRMetaBlockWriter/SRMetaBlockReader
     * <p>
     * +------------------+
     * |     header       |
     * +------------------+
     * |                  |
     * |  Authentication  |
     * |     Manager      |
     * |                  |
     * +------------------+
     * |     numUser      |
     * +------------------+
     * | User Identify 1  |
     * +------------------+
     * |      User        |
     * |  Authentication  |
     * |     Info 1       |
     * +------------------+
     * | User Identify 2  |
     * +------------------+
     * |      User        |
     * |  Authentication  |
     * |     Info 2       |
     * +------------------+
     * |       ...        |
     * +------------------+
     * |      footer      |
     * +------------------+
     */
    public void save(DataOutputStream dos) throws IOException {
        try {
            // 1 json for myself,1 json for number of users, 2 json for each user(kv)
            final int cnt = 1 + 1 + userToAuthenticationInfo.size() * 2;
            SRMetaBlockWriter writer = new SRMetaBlockWriter(dos, AuthenticationManager.class.getName(), cnt);
            // 1 json for myself
            writer.writeJson(this);
            // 1 json for num user
            writer.writeJson(userToAuthenticationInfo.size());
            Iterator<Map.Entry<UserIdentity, UserAuthenticationInfo>> iterator =
                    userToAuthenticationInfo.entrySet().iterator();
            while (iterator.hasNext()) {
                // 2 json for each user(kv)
                Map.Entry<UserIdentity, UserAuthenticationInfo> entry = iterator.next();
                writer.writeJson(entry.getKey());
                writer.writeJson(entry.getValue());
            }
            LOG.info("saved {} users", userToAuthenticationInfo.size());
            writer.close();
        } catch (SRMetaBlockException e) {
            IOException exception = new IOException("failed to save AuthenticationManager!");
            exception.initCause(e);
            throw exception;
        }
    }

    public static AuthenticationManager load(DataInputStream dis) throws IOException, DdlException {
        try {
            SRMetaBlockReader reader = new SRMetaBlockReader(dis, AuthenticationManager.class.getName());
            AuthenticationManager ret = null;
            try {
                // 1 json for myself
                ret = (AuthenticationManager) reader.readJson(AuthenticationManager.class);
                ret.userToAuthenticationInfo = new HashMap<>();
                // 1 json for num user
                int numUser = (int) reader.readJson(int.class);
                LOG.info("loading {} users", numUser);
                for (int i = 0; i != numUser; ++i) {
                    // 2 json for each user(kv)
                    UserIdentity userIdentity = (UserIdentity) reader.readJson(UserIdentity.class);
                    UserAuthenticationInfo userAuthenticationInfo =
                            (UserAuthenticationInfo) reader.readJson(UserAuthenticationInfo.class);
                    userAuthenticationInfo.analyze();
                    ret.userToAuthenticationInfo.put(userIdentity, userAuthenticationInfo);
                }
            } catch (SRMetaBlockEOFException eofException) {
                LOG.warn("got EOF exception, ignore, ", eofException);
            } finally {
                reader.close();
            }
            assert ret != null; // can't be NULL
            LOG.info("loaded {} users", ret.userToAuthenticationInfo.size());
            // mark data is loaded
            ret.isLoaded = true;
            return ret;
        } catch (SRMetaBlockException | AuthenticationException e) {
            throw new DdlException("failed to load AuthenticationManager!", e);
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void setLoaded() {
        isLoaded = true;
    }

    /**
     * these public interfaces are for AuthUpgrader to upgrade from 2.x
     */
    public void upgradeUserUnlocked(UserIdentity userIdentity, Password password) throws AuthenticationException {
        AuthPlugin plugin = password.getAuthPlugin();
        if (plugin == null) {
            plugin = AuthPlugin.MYSQL_NATIVE_PASSWORD;
        }
        AuthenticationProvider provider = AuthenticationProviderFactory.create(plugin.toString());
        UserAuthenticationInfo info = provider.upgradedFromPassword(userIdentity, password);
        userToAuthenticationInfo.put(userIdentity, info);
        LOG.info("upgrade user {}", userIdentity);
    }

    public UserAuthenticationInfo getUserAuthenticationInfoByUserIdentity(UserIdentity userIdentity) {
        return userToAuthenticationInfo.get(userIdentity);
    }

    public Map<UserIdentity, UserAuthenticationInfo> getUserToAuthenticationInfo() {
        return userToAuthenticationInfo;
    }

    public void upgradeUserProperty(String userName, long maxConn) {
        UserProperty userProperty = new UserProperty();
        userProperty.setMaxConn(maxConn);
        userNameToProperty.put(userName, new UserProperty());
    }
}
