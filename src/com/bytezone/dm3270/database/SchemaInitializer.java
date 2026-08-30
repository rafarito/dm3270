package com.bytezone.dm3270.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * O esquema do banco: as duas tabelas, e nada mais.
 *
 * Estava dentro do DatabaseThread, em dois metodos privados de setenta e poucas linhas que
 * respondiam pelos comandos OPEN, CREATE e DROP. O DDL nao tem nada a ver com o laco da fila
 * nem com o despacho de requisicoes - e a forma do banco, e muda pelos seus proprios motivos.
 *
 * As colunas, os comentarios de cada uma, as chaves e o WITHOUT ROWID sao os de antes, palavra
 * por palavra. Note que as duas tabelas declaram colunas que o codigo nunca le nem grava - o
 * bloco "PC details" inteiro, mais CYLINDERS -, e isso tambem fica como esta: sao colunas
 * previstas para o download que ainda nao foi escrito, e mexer nelas mudaria o arquivo que os
 * usuarios ja tem no disco.
 *
 * Os dois catch sao diferentes de proposito: create apanha Exception e drop apanha
 * SQLException. E assim desde antes, e nao ha motivo aparente - so nao ha motivo para mudar
 * junto com uma reorganizacao.
 *
 * O logger e o do DatabaseThread. O padrao do logback imprime %logger{36}, entao o nome da
 * classe faz parte de toda linha - declarar um logger proprio aqui mudaria saida observavel.
 */
// -----------------------------------------------------------------------------------//
final class SchemaInitializer
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private final Connection connection;

  // ---------------------------------------------------------------------------------//
  SchemaInitializer (Connection connection)
  // ---------------------------------------------------------------------------------//
  {
    this.connection = connection;
  }

  // ---------------------------------------------------------------------------------//
  boolean create ()
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      Statement stmt = connection.createStatement ();

      String sql = "create table if not exists DATASETS ("     //
          + "NAME           TEXT NOT NULL,"      //

          // mainframe details
          + "VOLUME         TEXT         ,"      // FUSRxx
          + "DEVICE         TEXT         ,"      // 3390
          + "CATALOG        TEXT         ,"      // CATALOG.USER.UCAT

          + "CREATED        DATE         ,"      //
          + "EXPIRES        DATE         ,"      //
          + "REFERRED       DATE         ,"      //

          + "TRACKS         INT          ,"      //
          + "CYLINDERS      INT          ,"      //
          + "PERCENT        INT          ,"      //
          + "EXTENTS        INT          ,"      //

          + "DSORG          TEXT         ,"      // PO, PS
          + "RECFM          TEXT         ,"      // FB, VB
          + "LRECL          INT          ,"      //
          + "BLKSIZE        INT          ,"      //

          // PC details
          + "FILENAME       TEXT         ,"      // local filename (?)
          + "DOWNLOADED     DATE         ,"      // downloaded date/time
          + "CREATED2       DATE         ,"      // created date when downloaded
          + "REFERRED2      DATE         ,"      // referred date when downloaded
          + "ENCODING       TEXT         ,"      // ascii/ebcdic
          + "STRUCTURE      TEXT         ,"      // cr/reclen/ravel/rdw etc

          + "PRIMARY KEY (NAME)"                 //
          + ") WITHOUT ROWID";

      stmt.executeUpdate (sql);
      stmt.close ();

      stmt = connection.createStatement ();

      sql = "create table if not exists MEMBERS ("      //
          + "DATASET        TEXT NOT NULL,"      //
          + "NAME           TEXT NOT NULL,"      //

          // mainframe details
          + "SIZE           INT          ,"      //
          + "INIT           INT          ,"      //
          + "MOD            INT          ,"      //
          + "VV             INT          ,"      //
          + "MM             INT          ,"      //
          + "ID             TEXT         ,"      //
          + "CREATED        DATE         ,"      //
          + "CHANGED        DATE         ,"      //

          // PC details
          + "FILENAME       TEXT         ,"      // local filename (?)
          + "DOWNLOADED     DATE         ,"      // downloaded date/time
          + "CREATED2       DATE         ,"      // created date when downloaded
          + "CHANGED2       DATE         ,"      // changed date when downloaded
          + "ENCODING       TEXT         ,"      // ascii/ebcdic
          + "STRUCTURE      TEXT         ,"      // cr/reclen/ravel/rdw etc

          + "FOREIGN KEY (DATASET) REFERENCES DATASETS (NAME),"
          + "PRIMARY KEY (DATASET, NAME)"                   //
          + ") WITHOUT ROWID";

      stmt.executeUpdate (sql);
      stmt.close ();

      return true;
    }
    catch (Exception e)
    {
      logger.error ("Error creating database tables", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  boolean drop ()
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      Statement stmt = connection.createStatement ();
      stmt.executeUpdate ("drop table if exists DATASETS");
      stmt.close ();

      stmt = connection.createStatement ();
      stmt.executeUpdate ("drop table if exists MEMBERS");
      stmt.close ();

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error dropping tables", e);
      return false;
    }
  }
}
