package com.bytezone.dm3270.database;

import com.bytezone.dm3270.datasets.Dataset;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// Ordem dos parametros em bind() deve casar com os SQLs em DatasetRepository.
// -----------------------------------------------------------------------------------//
final class DatasetMapper
// -----------------------------------------------------------------------------------//
{
  private DatasetMapper ()
  {
  }

  // ---------------------------------------------------------------------------------//
  static void bind (PreparedStatement ps, Dataset dataset) throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    ps.setString (1, dataset.getVolume ());
    ps.setString (2, dataset.getDevice ());
    ps.setString (3, dataset.getCatalog ());
    ps.setInt (4, dataset.getTracks ());
    ps.setInt (5, dataset.getCylinders ());
    ps.setInt (6, dataset.getPercent ());
    ps.setInt (7, dataset.getExtents ());
    ps.setString (8, dataset.getDsorg ());
    ps.setString (9, dataset.getRecfm ());
    ps.setInt (10, dataset.getLrecl ());
    ps.setInt (11, dataset.getBlksize ());
    ps.setDate (12, dataset.getCreatedSQL () == null ? null : dataset.getCreatedSQL ());
    ps.setDate (13, dataset.getExpiresSQL () == null ? null : dataset.getExpiresSQL ());
    ps.setDate (14, dataset.getReferredSQL () == null ? null : dataset.getReferredSQL ());
    ps.setString (15, dataset.getName ());
  }

  // ---------------------------------------------------------------------------------//
  static Dataset read (ResultSet rs) throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    Dataset dataset = new Dataset (rs.getString ("name"));

    dataset.setSpace (rs.getInt ("tracks"), rs.getInt ("cylinders"),
        rs.getInt ("extents"), rs.getInt ("percent"));
    dataset.setDisposition (rs.getString ("dsorg"), rs.getString ("recfm"),
        rs.getInt ("lrecl"), rs.getInt ("blksize"));
    dataset.setLocation (rs.getString ("volume"), rs.getString ("device"),
        rs.getString ("catalog"));
    dataset.setDates (rs.getDate ("created"), rs.getDate ("expires"),
        rs.getDate ("referred"));

    return dataset;
  }
}
