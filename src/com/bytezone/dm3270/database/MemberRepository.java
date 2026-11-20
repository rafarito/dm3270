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
import com.bytezone.dm3270.datasets.Member;

// Usa o logger de DatabaseThread para preservar o formato de saida do logback.
// -----------------------------------------------------------------------------------//
final class MemberRepository
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private static final String INSERT_MEMBER =
      "insert into MEMBERS (ID, SIZE, INIT, MOD, VV, MM, CREATED, "
          + "CHANGED, DATASET, NAME) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String UPDATE_MEMBER =
      "update MEMBERS set ID=?, SIZE=?, INIT=?, MOD=?, VV=?, MM=?, CREATED=?, "
          + "CHANGED=? where DATASET=? and NAME=?";

  private final Connection connection;
  private final DatasetCache cache;
  private final DatasetRepository datasets;

  // ---------------------------------------------------------------------------------//
  MemberRepository (Connection connection, DatasetCache cache, DatasetRepository datasets)
  // ---------------------------------------------------------------------------------//
  {
    this.connection = connection;
    this.cache = cache;
    this.datasets = datasets;
  }

  // Recarrega o Dataset do banco apos achar o membro, pois o parametro pode vir incompleto.
  // ---------------------------------------------------------------------------------//
  Optional<Member> find (Dataset dataset, String memberName)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      Statement stmt = connection.createStatement ();
      ResultSet rs = stmt.executeQuery ("select * from MEMBERS where DATASET='"
          + dataset.getName () + "' and NAME='" + memberName + "'");

      if (rs.next ())
      {
        Optional<Dataset> optDataset = datasets.find (dataset.getName ());
        if (optDataset.isPresent ())
          dataset = optDataset.get ();        // replace the parameter we were given

        Member member = MemberMapper.read (rs, dataset);
        cache.putMember (dataset, member);
        return Optional.of (member);
      }
    }
    catch (SQLException e)
    {
      logger.error ("Error finding member", e);
    }
    return Optional.empty ();
  }

  // Busca o dataset antes para popular o cache usado em addMember.
  // Filtro com '*' apos pos 0 gera "where" duplicado (comportamento legado).
  // ---------------------------------------------------------------------------------//
  boolean list (String datasetName, String memberName, List<Member> into)
  // ---------------------------------------------------------------------------------//
  {
    Optional<Dataset> optDataset = datasets.find (datasetName);
    if (!optDataset.isPresent ())
      return false;
    Dataset dataset = optDataset.get ();

    try
    {
      Statement stmt = connection.createStatement ();

      String query = "select * from MEMBERS where DATASET='" + datasetName + "'";
      int pos = memberName.indexOf ('*');

      if (pos > 0)
      {
        String from = memberName.substring (0, pos);
        String to = from + "Z";
        query += "where NAME>='" + from + "' and NAME<='" + to + "'";
      }

      ResultSet rs = stmt.executeQuery (query);
      while (rs.next ())
      {
        Member member = MemberMapper.read (rs, dataset);
        into.add (member);
        cache.addMember (dataset, member);
      }

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error creating member list", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  boolean insert (String datasetName, Member member)
  // ---------------------------------------------------------------------------------//
  {
    Optional<Dataset> optDataset = datasets.find (datasetName);
    if (optDataset.isPresent ())
    {
      Dataset dataset = optDataset.get ();
      if (dataset.getDsorg () == null)
        datasets.markPartitioned (dataset);
    }
    else
      datasets.insertPartitioned (datasetName);

    try
    {
      PreparedStatement ps2 = connection.prepareStatement (INSERT_MEMBER);

      MemberMapper.bind (ps2, member);
      ps2.executeUpdate ();
      ps2.close ();

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error inserting member", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  boolean update (Member member)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      logger.info ("Member modified: {}({})", member.getDataset ().getName (),
          member.getName ());
      logger.info ("{}", member);

      PreparedStatement ps4 = connection.prepareStatement (UPDATE_MEMBER);

      MemberMapper.bind (ps4, member);
      ps4.executeUpdate ();
      ps4.close ();

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error updating member", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  boolean delete (Member member)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      Statement stmt = connection.createStatement ();
      stmt.executeUpdate ("delete from MEMBERS where DATASET='"
          + member.getDataset ().getName () + "' and NAME='" + member.getName () + "'");
      stmt.close ();
      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error deleting member", e);
      return false;
    }
  }
}
