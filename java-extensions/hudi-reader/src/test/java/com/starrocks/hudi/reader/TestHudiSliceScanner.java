package com.starrocks.hudi.reader;

import com.starrocks.jni.connector.OffHeapTable;
import com.starrocks.utils.Platform;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class TestHudiSliceScanner {

    @Before
    public void setUp() {
        System.setProperty("starrocks.fe.test", "1");
    }

    @After
    public void tearDown() {
        System.setProperty("starrocks.fe.test", "0");
    }

    /*

CREATE TABLE `test_hudi_mor` (
  `uuid` STRING,
  `ts` int,
  `a` int,
  `b` string,
  `c` array<int>,
  `d` map<string, int>,
  `e` struct<a:int, b:string>)
  USING hudi
TBLPROPERTIES (
  'primaryKey' = 'uuid',
  'preCombineField' = 'ts',
  'type' = 'mor');

spark-sql> select a,b,c,d,e from test_hudi_mor;
a       b       c       d       e
1       hello   [10,20,30]      {"key1":1,"key2":2}     {"a":10,"b":"world"}
*/

    Map<String, String> createScanTestParams() {
        Map<String, String> params = new HashMap<>();
        URL resource = TestHudiSliceScanner.class.getResource("/test_hudi_mor");
        String basePath = resource.getPath().toString();
        params.put("base_path", basePath);
        params.put("data_file_path",
                basePath + "/64798197-be6a-4eca-9898-0c2ed75b9d65-0_0-54-41_20230105142938081.parquet");
        params.put("delta_file_paths",
                basePath + "/.64798197-be6a-4eca-9898-0c2ed75b9d65-0_20230105142938081.log.1_0-95-78");
        params.put("hive_column_names",
                "_hoodie_commit_time,_hoodie_commit_seqno,_hoodie_record_key,_hoodie_partition_path,_hoodie_file_name,uuid,ts,a,b,c,d,e");
        params.put("hive_column_types",
                "string#string#string#string#string#string#int#int#string#array<int>#map<string,int>#struct<a:int,b:string>");
        params.put("instant_time", "20230105143305070");
        params.put("data_file_length", "436081");
        params.put("input_format", "org.apache.hudi.hadoop.realtime.HoodieParquetRealtimeInputFormat");
        params.put("serde", "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe");
        params.put("required_fields", "a,b");
        return params;
    }

    void runScanOnParams(Map<String, String> params) throws IOException {
        HudiSliceScanner scanner = new HudiSliceScanner(4096, params);
        System.out.println(scanner.toString());
        scanner.open();
        while (true) {
            scanner.getNextOffHeapChunk();
            OffHeapTable table = scanner.getOffHeapTable();
            if (table.getNumRows() == 0) {
                break;
            }
            table.show(10);
            table.checkTableMeta(true);
            table.close();
        }
        scanner.close();
    }

    @Test
    public void doScanTestOnPrimitiveType() throws IOException {
        Map<String, String> params = createScanTestParams();
        runScanOnParams(params);
    }

    @Test
    public void doScanTestOnArrayType() throws IOException {
        Map<String, String> params = createScanTestParams();
        params.put("required_fields", "c");
        runScanOnParams(params);
    }

    @Test
    public void doScanTestOnMapType() throws IOException {
        Map<String, String> params = createScanTestParams();
        params.put("required_fields", "d");
        runScanOnParams(params);
    }

    @Test
    public void doScanTestOnStructType() throws IOException {
        Map<String, String> params = createScanTestParams();
        params.put("required_fields", "e");
        params.put("nested_fields", "e.a,e.b");
        runScanOnParams(params);
    }

    @Test
    public void doScanTestOnPruningStructType() throws IOException {
        Map<String, String> params = createScanTestParams();
        params.put("required_fields", "e");
        params.put("nested_fields", "e.b");
        runScanOnParams(params);
    }

        /*


CREATE TABLE `test_hudi_mor2` (
  `uuid` STRING,
  `ts` int,
  `a` int,
  `b` string,
  `c` array<array<int>>,
  `d` map<string, array<int>>,
  `e` struct<a:array<int>, b:map<string,int>, c:struct<a:array<int>, b:struct<a:int,b:string>>>)
  USING hudi
TBLPROPERTIES (
  'primaryKey' = 'uuid',
  'preCombineField' = 'ts',
  'type' = 'mor');

insert into test_hudi_mor2 values('AA0', 10, 0, "hello", array(array(10,20,30), array(40,50,60,70) ), map('key1', array(1,10), 'key2', array(2, 20), 'key3', null), struct(array(10, 20), map('key1', 10), struct(array(10, 20), struct(10, "world")))),
 ('AA1', 10, 0, "hello", null, null , struct(null, map('key1', 10), struct(array(10, 20), struct(10, "world")))),
 ('AA2', 10, 0, null, array(array(30, 40), array(10,20,30)), null , struct(null, map('key1', 10), struct(array(10, 20), null)));

spark-sql> select a,b,c,d,e from test_hudi_mor2;
a       b       c       d       e
0       hello   NULL    NULL    {"a":null,"b":{"key1":10},"c":{"a":[10,20],"b":{"a":10,"b":"world"}}}
0       NULL    [[30,40],[10,20,30]]    NULL    {"a":null,"b":{"key1":10},"c":{"a":[10,20],"b":null}}
0       hello   [[10,20,30],[40,50,60,70]]      {"key1":[1,10],"key2":[2,20],"key3":null}       {"a":[10,20],"b":{"key1":10},"c":{"a":[10,20],"b":{"a":10,"b":"world"}}}
    */

    Map<String, String> case2CreateScanTestParams() {
        Map<String, String> params = new HashMap<>();
        URL resource = TestHudiSliceScanner.class.getResource("/test_hudi_mor2");
        String basePath = resource.getPath().toString();
        params.put("base_path", basePath);
        params.put("data_file_path",
                basePath + "/0df0196b-f46f-43f5-8cf0-06fad7143af3-0_0-27-35_20230110191854854.parquet");
        params.put("delta_file_paths", "");
        params.put("hive_column_names",
                "_hoodie_commit_time,_hoodie_commit_seqno,_hoodie_record_key,_hoodie_partition_path,_hoodie_file_name,uuid,ts,a,b,c,d,e");
        params.put("hive_column_types",
                "string#string#string#string#string#string#int#int#string#array<array<int>>#map<string,array<int>>#struct<a:array<int>,b:map<string,int>,c:struct<a:array<int>,b:struct<a:int,b:string>>>");
        params.put("instant_time", "20230110185815638");
        params.put("data_file_length", "438311");
        params.put("input_format", "org.apache.hudi.hadoop.realtime.HoodieParquetRealtimeInputFormat");
        params.put("serde", "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe");
        params.put("required_fields", "a,b");
        return params;
    }

    @Test
    public void case2doScanTestOnMapArrayType() throws IOException {
        Map<String, String> params = case2CreateScanTestParams();
        params.put("required_fields", "a,b,c,d");
        runScanOnParams(params);
    }

    @Test
    public void case2doScanTestOnStructType() throws IOException {
        Map<String, String> params = case2CreateScanTestParams();
        params.put("required_fields", "e");
        params.put("nested_fields", "e.b,e.a");
        runScanOnParams(params);
    }

    @Test
    public void case2doScanTestOnStructType2() throws IOException {
        Map<String, String> params = case2CreateScanTestParams();
        params.put("required_fields", "e");
        params.put("nested_fields", "e.c.a,e.b,e.a");
        runScanOnParams(params);
    }

    @Test
    public void case2doScanTestOnStructType3() throws IOException {
        Map<String, String> params = case2CreateScanTestParams();
        params.put("required_fields", "e");
        params.put("nested_fields", "e.c.b.a");
        runScanOnParams(params);
    }

    @Test
    public void case2doScanTestOnStructType4() throws IOException {
        Map<String, String> params = case2CreateScanTestParams();
        params.put("required_fields", "e");
        params.put("nested_fields", "e.c.b.b,e.c.b.a,e.c.a,e.b,e.a");
        runScanOnParams(params);
    }
}
