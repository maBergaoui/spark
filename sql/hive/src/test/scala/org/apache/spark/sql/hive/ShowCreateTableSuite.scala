/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.hive

import org.apache.spark.sql.{AnalysisException, QueryTest}
import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.catalyst.catalog.CatalogTable
import org.apache.spark.sql.hive.test.TestHiveSingleton
import org.apache.spark.sql.test.SQLTestUtils
import org.apache.spark.util.Utils

class ShowCreateTableSuite extends QueryTest with SQLTestUtils with TestHiveSingleton {
  import testImplicits._

  test("data source table with user specified schema") {
    withTable("ddl_test1") {
      val jsonFilePath = Utils.getSparkClassLoader.getResource("sample.json").getFile

      sql(
        s"""CREATE TABLE ddl_test1 (
           |  a STRING,
           |  b STRING,
           |  `extra col` ARRAY<INT>,
           |  `<another>` STRUCT<x: INT, y: ARRAY<BOOLEAN>>
           |)
           |USING json
           |OPTIONS (
           | PATH '$jsonFilePath'
           |)
         """.stripMargin
      )

      checkCreateTable("ddl_test1")
    }
  }

  test("data source table CTAS") {
    withTable("ddl_test2") {
      sql(
        s"""CREATE TABLE ddl_test2
           |USING json
           |AS SELECT 1 AS a, "foo" AS b
         """.stripMargin
      )

      checkCreateTable("ddl_test2")
    }
  }

  test("partitioned data source table") {
    withTable("ddl_test3") {
      sql(
        s"""CREATE TABLE ddl_test3
           |USING json
           |PARTITIONED BY (b)
           |AS SELECT 1 AS a, "foo" AS b
         """.stripMargin
      )

      checkCreateTable("ddl_test3")
    }
  }

  test("bucketed data source table") {
    withTable("ddl_test3") {
      sql(
        s"""CREATE TABLE ddl_test3
           |USING json
           |CLUSTERED BY (a) SORTED BY (b) INTO 2 BUCKETS
           |AS SELECT 1 AS a, "foo" AS b
         """.stripMargin
      )

      checkCreateTable("ddl_test3")
    }
  }

  test("partitioned bucketed data source table") {
    withTable("ddl_test4") {
      sql(
        s"""CREATE TABLE ddl_test4
           |USING json
           |PARTITIONED BY (c)
           |CLUSTERED BY (a) SORTED BY (b) INTO 2 BUCKETS
           |AS SELECT 1 AS a, "foo" AS b, 2.5 AS c
         """.stripMargin
      )

      checkCreateTable("ddl_test4")
    }
  }

  test("data source table using Dataset API") {
    withTable("ddl_test5") {
      spark
        .range(3)
        .select('id as 'a, 'id as 'b, 'id as 'c, 'id as 'd, 'id as 'e)
        .write
        .mode("overwrite")
        .partitionBy("a", "b")
        .bucketBy(2, "c", "d")
        .saveAsTable("ddl_test5")

      checkCreateTable("ddl_test5")
    }
  }

  test("simple hive table") {
    withTable("t1") {
      sql(
        s"""CREATE TABLE t1 (
           |  c1 INT COMMENT 'bla',
           |  c2 STRING
           |)
           |TBLPROPERTIES (
           |  'prop1' = 'value1',
           |  'prop2' = 'value2'
           |)
         """.stripMargin
      )

      checkCreateTable("t1")
    }
  }

  test("simple external hive table") {
    withTempDir { dir =>
      withTable("t1") {
        sql(
          s"""CREATE TABLE t1 (
             |  c1 INT COMMENT 'bla',
             |  c2 STRING
             |)
             |LOCATION '$dir'
             |TBLPROPERTIES (
             |  'prop1' = 'value1',
             |  'prop2' = 'value2'
             |)
           """.stripMargin
        )

        checkCreateTable("t1")
      }
    }
  }

  test("partitioned hive table") {
    withTable("t1") {
      sql(
        s"""CREATE TABLE t1 (
           |  c1 INT COMMENT 'bla',
           |  c2 STRING
           |)
           |COMMENT 'bla'
           |PARTITIONED BY (
           |  p1 BIGINT COMMENT 'bla',
           |  p2 STRING
           |)
         """.stripMargin
      )

      checkCreateTable("t1")
    }
  }

  test("hive table with explicit storage info") {
    withTable("t1") {
      sql(
        s"""CREATE TABLE t1 (
           |  c1 INT COMMENT 'bla',
           |  c2 STRING
           |)
           |ROW FORMAT DELIMITED FIELDS TERMINATED BY ','
           |COLLECTION ITEMS TERMINATED BY '@'
           |MAP KEYS TERMINATED BY '#'
           |NULL DEFINED AS 'NaN'
         """.stripMargin
      )

      checkCreateTable("t1")
    }
  }

  test("hive table with STORED AS clause") {
    withTable("t1") {
      sql(
        s"""CREATE TABLE t1 (
           |  c1 INT COMMENT 'bla',
           |  c2 STRING
           |)
           |STORED AS PARQUET
         """.stripMargin
      )

      checkCreateTable("t1")
    }
  }

  test("hive table with serde info") {
    withTable("t1") {
      sql(
        s"""CREATE TABLE t1 (
           |  c1 INT COMMENT 'bla',
           |  c2 STRING
           |)
           |ROW FORMAT SERDE 'org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe'
           |WITH SERDEPROPERTIES (
           |  'mapkey.delim' = ',',
           |  'field.delim' = ','
           |)
           |STORED AS
           |  INPUTFORMAT 'org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat'
           |  OUTPUTFORMAT 'org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat'
         """.stripMargin
      )

      checkCreateTable("t1")
    }
  }

  test("hive view") {
    withView("v1") {
      sql("CREATE VIEW v1 AS SELECT 1 AS a")
      checkCreateView("v1")
    }
  }

  test("hive view with output columns") {
    withView("v1") {
      sql("CREATE VIEW v1 (b) AS SELECT 1 AS a")
      checkCreateView("v1")
    }
  }

  test("hive bucketing is not supported") {
    withTable("t1") {
      createRawHiveTable(
        s"""CREATE TABLE t1 (a INT, b STRING)
           |CLUSTERED BY (a)
           |SORTED BY (b)
           |INTO 2 BUCKETS
         """.stripMargin
      )

      val cause = intercept[AnalysisException] {
        sql("SHOW CREATE TABLE t1")
      }

      assert(cause.getMessage.contains(" - bucketing"))
    }
  }

  private def createRawHiveTable(ddl: String): Unit = {
    hiveContext.sharedState.metadataHive.runSqlHive(ddl)
  }

  private def checkCreateTable(table: String): Unit = {
    checkCreateTableOrView(TableIdentifier(table, Some("default")), "TABLE")
  }

  private def checkCreateView(table: String): Unit = {
    checkCreateTableOrView(TableIdentifier(table, Some("default")), "VIEW")
  }

  private def checkCreateTableOrView(table: TableIdentifier, checkType: String): Unit = {
    val db = table.database.getOrElse("default")
    val expected = spark.externalCatalog.getTable(db, table.table)
    val shownDDL = sql(s"SHOW CREATE TABLE ${table.quotedString}").head().getString(0)
    sql(s"DROP $checkType ${table.quotedString}")

    try {
      sql(shownDDL)
      val actual = spark.externalCatalog.getTable(db, table.table)
      checkCatalogTables(expected, actual)
    } finally {
      sql(s"DROP $checkType IF EXISTS ${table.table}")
    }
  }

  private def checkCatalogTables(expected: CatalogTable, actual: CatalogTable): Unit = {
    def normalize(table: CatalogTable): CatalogTable = {
      val nondeterministicProps = Set(
        "CreateTime",
        "transient_lastDdlTime",
        "grantTime",
        "lastUpdateTime",
        "last_modified_by",
        "last_modified_time",
        "Owner:",
        "COLUMN_STATS_ACCURATE",
        // The following are hive specific schema parameters which we do not need to match exactly.
        "numFiles",
        "numRows",
        "rawDataSize",
        "totalSize",
        "totalNumberFiles",
        "maxFileSize",
        "minFileSize",
        // EXTERNAL is not non-deterministic, but it is filtered out for external tables.
        "EXTERNAL"
      )

      table.copy(
        createTime = 0L,
        lastAccessTime = 0L,
        properties = table.properties.filterKeys(!nondeterministicProps.contains(_)),
        // View texts are checked separately
        viewOriginalText = None,
        viewText = None
      )
    }

    // Normalizes attributes auto-generated by Spark SQL for views
    def normalizeGeneratedAttributes(str: String): String = {
      str.replaceAll("gen_attr_[0-9]+", "gen_attr_0")
    }

    // We use expanded canonical view text as original view text of the new table
    assertResult(expected.viewText.map(normalizeGeneratedAttributes)) {
      actual.viewOriginalText.map(normalizeGeneratedAttributes)
    }

    assert(normalize(actual) == normalize(expected))
  }
}
