# strleft

## Description

This function extracts a number of characters from a string with specified length (starting from left). The unit for length: utf8 character.

## Syntax

```Haskell
VARCHAR strleft(VARCHAR str,INT len)
```

## Examples

```Plain Text
MySQL > select strleft("Hello starrocks",5);
+-------------------------+
|strleft('Hello starrocks', 5)|
+-------------------------+
| Hello                   |
+-------------------------+
```

## keyword

STRLEFT
