package com.bytezone.dm3270.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.datasets.Dataset;

// Usa o logger de DatabaseThread para preservar o formato de saida do logback.
// -----------------------------------------------------------------------------------//
final class DatasetRepository
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private static final String INSERT_DATASET =
      "insert into DATASETS (VOLUME, DEVICE, CATALOG, "
          + "TRACKS, CYLINDERS, PERCENT, EXTENTS, DSORG, RECFM, LRECL, BLKSIZE,"
          + "CREATED, EXPIRES, REFERRED, NAME) "
          + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String UPDATE_DATASET = "update DATASETS set VOLUME=?, DEVICE=?, "
      + "CATALOG=?, TRACKS=?, CYLINDERS=?, PERCENT=?, EXTENTS=?, DSORG=?, RECFM=?, "
      + "LRECL=?, BLKSIZE=?, CREATED=?, EXPIRES=?, REFERRED=? where NAME=?";

  private final Connection connection;
  private final DatasetCache cache;

  // ---------------------------------------------------------------------------------//
  DatasetRepository (Connection connection, DatasetCache cache)
  // ---------------------------------------------------------------------------------//
  {
    this.connection = connection;
    this.cache = cache;
  }

  // ---------------------------------------------------------------------------------//
  Optional<Dataset> find (String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      Statement stmt = connection.createStatement ();
      ResultSet rs =
          stmt.executeQuery ("SELECT * FROM DATASETS where NAME = '" + datasetName + "'");
      if (rs.next ())
      {
        Dataset dataset = DatasetMapper.read (rs);
        cache.rememberIfAbsent (dataset);
        return Optional.of (dataset);
      }
    }
    catch (SQLException e)
    {
      logger.error ("Error finding dataset", e);
    }
    return Optional.empty ();
  }

  // ---------------------------------------------------------------------------------//
  boolean list (String filter, List<Dataset> into)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      Statement stmt = connection.createStatement ();

      String query = "select * from DATASETS ";
      int pos = filter.isEmpty () ? 0 : filter.indexOf ('*');

      if (pos < 0)
        query += "where NAME='" + filter + "'";
      else if (pos > 0)
      {
        String from = filter.substring (0, pos);
        String to = from + "Z";
        query += "where NAME>='" + from + "' and NAME<='" + to + "'";
      }

      ResultSet rs = stmt.executeQuery (query);
      while (rs.next ())
      {
        Dataset dataset = DatasetMapper.read (rs);
        into.add (dataset);
        cache.remember (dataset);             // is this necessary?
      }

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error creating dataset list", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  boolean insert (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      PreparedStatement ps1 = connection.prepareStatement (INSERT_DATASET);

      DatasetMapper.bind (ps1, dataset);
      ps1.executeUpdate ();
      ps1.close ();

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error inserting dataset", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  boolean update (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      logger.info ("Dataset modified:");
      logger.info ("{}", dataset);

      PreparedStatement ps3 = connection.prepareStatement (UPDATE_DATASET);

      DatasetMapper.bind (ps3, dataset);
      ps3.executeUpdate ();
      ps3.close ();

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error updating dataset", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  boolean delete (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      connection.setAutoCommit (false);

      // delete members
      Statement stmt = connection.createStatement ();
      stmt.executeUpdate (
          "delete from MEMBERS where DATASET='" + dataset.getName () + "'");
      stmt.close ();

      // delete dataset
      stmt = connection.createStatement ();
      stmt.executeUpdate ("delete from DATASETS where NAME='" + dataset.getName () + "'");
      stmt.close ();

      connection.commit ();
      connection.setAutoCommit (true);

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error deleting dataset", e);
      try
      {
        connection.rollback ();
      }
      catch (SQLException e1)
      {
        logger.error ("Error rolling back transaction", e1);
      }
      return false;
    }
  }

  // Falhas sao apenas logadas para nao bloquear a insercao do membro.
  // ---------------------------------------------------------------------------------//
  void markPartitioned (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    dataset.markPartitioned ();
    String cmd =
        "update DATASETS set DSORG='PO' where NAME='" + dataset.getName () + "'";
    try
    {
      Statement stmt = connection.createStatement ();
      stmt.executeUpdate (cmd);
      stmt.close ();
    }
    catch (SQLException e)
    {
      logger.error ("Error updating dataset DSORG", e);
    }
  }

  // ---------------------------------------------------------------------------------//
  void insertPartitioned (String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    Dataset dataset = new Dataset (datasetName);
    dataset.markPartitioned ();
    String cmd =
        "insert into DATASETS (NAME,DSORG) values ('" + dataset.getName () + "', 'PO')";
    try
    {
      Statement stmt = connection.createStatement ();
      stmt.executeUpdate (cmd);
      stmt.close ();
    }
    catch (SQLException e)
    {
      logger.error ("Error inserting dataset", e);
    }
  }
}
