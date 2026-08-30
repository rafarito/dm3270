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

/*
 * O SQL da tabela MEMBERS. Mesma divisao do DatasetRepository: operacao de banco aqui, e a
 * regra no DatabaseThread.
 *
 * Depende do DatasetRepository, e a dependencia e real e nao acidental: um membro so existe
 * dentro de um dataset. O find recarrega o dataset dono antes de montar o Member, e o insert
 * garante que o dataset exista e esteja marcado como particionado antes de gravar - criando-o
 * se preciso. As duas coisas sao SQL de DATASETS, e por isso ficam do outro lado.
 *
 * O logger e o do DatabaseThread, pelo mesmo motivo do DatasetRepository: o padrao do logback
 * imprime %logger{36}.
 */
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

  /*
   * A releitura do dataset acontece DEPOIS de saber que a linha do membro existe, e nao antes:
   * quem chamou pode ter passado um Dataset montado da tela, sem os campos que o banco tem, e
   * o Member precisa apontar para o do banco. Se a releitura falhar, o parametro original
   * segue valendo.
   */
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

  /*
   * Sem o dataset dono nao ha lista: a busca por ele tambem e o que garante a entrada no cache
   * que o laco abaixo usa.
   *
   * O filtro por nome de membro monta um segundo "where" quando o curinga vem depois da
   * primeira posicao, e o SQL sai invalido - item 11 do BACKLOG-DEFEITOS.md. Esta preservado,
   * e ha teste congelando a falha.
   */
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

  /*
   * O dataset dono vem primeiro. Se ja existe e nao tem dsorg, passa a particionado; se nao
   * existe, e criado ja particionado. Uma falha em qualquer um dos dois e so registrada, e a
   * insercao do membro segue - e o comportamento de antes.
   */
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

  // As duas linhas de log saem antes da escrita, e em chamadas separadas, como antes.
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
