# JDBCUtil
在这个轻量级的工具类当中，使用了数据库连接池去提高数据库连接的高效性，并且使用了PreparedStatement来执行对SQL的预编译，能够有效防止SQL注入问题。

# 配置文件
在src文件下创建dbconfig.properties文件，并填写以下信息。
```
driver = com.mysql.jdbc.Driver
url = jdbc:mysql://127.0.0.1:3306/test?characterEncoding=utf8
username = root
password = root
jdbcConnectionInitSize =10
```
**注：**properties是一种通用简单的配置文件格式，以键值对作为其配置语法，继承于HashMap，故Java对其有十分方便和高效的读取以及管理方法。
