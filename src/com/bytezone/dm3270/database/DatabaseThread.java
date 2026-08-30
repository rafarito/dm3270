package com.bytezone.dm3270.database;

import static com.bytezone.dm3270.database.DatabaseRequest.Command.LIST;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.Member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;

import com.bytezone.dm3270.database.DatabaseRequest.Result;

// -----------------------------------------------------------------------------------//
public class DatabaseThread extends Thread
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private Connection connection;
  private BlockingQueue<DatabaseRequest> queue;
  private boolean cancelled;
  private final String databaseName;
  private final SchemaInitializer schema;

  private final DatasetCache cache = new DatasetCache ();
  private final DatasetRepository datasets;
  private final MemberRepository members;

  // ---------------------------------------------------------------------------------//
  public DatabaseThread (String databaseName, BlockingQueue<DatabaseRequest> queue)
  // ---------------------------------------------------------------------------------//
  {
    this.databaseName = databaseName;
    this.queue = queue;

    try
    {
      Class.forName ("org.sqlite.JDBC");     // add sqlite JDBC Driver to DriverManager
      Path path = Paths.get (System.getProperty ("user.home"), "dm3270", "databases",
          databaseName);
      Files.createDirectories (path.getParent ());
      String connectionName = "jdbc:sqlite:" + path.toString ();
      connection = DriverManager.getConnection (connectionName);
      connection.setAutoCommit (true);
    }
    catch (Exception e)
    {
      cancelled = true;
      logger.error ("Error initializing database connection", e);
    }

    schema = new SchemaInitializer (connection);
    datasets = new DatasetRepository (connection, cache);
    members = new MemberRepository (connection, cache, datasets);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void run ()
  // ---------------------------------------------------------------------------------//
  {
    while (!cancelled)
    {
      try
      {
        DatabaseRequest request = queue.take ();

        request.result = Result.FAILURE;
        request.databaseName = databaseName;

        if (request instanceof DatasetRequest)
          process ((DatasetRequest) request);
        else if (request instanceof MemberRequest)
          process ((MemberRequest) request);
        else
          process (request);

        request.initiator.processResult (request);
      }
      catch (InterruptedException e)
      {
        logger.info ("interrupted");
        cancelled = true;
        Thread.currentThread ().interrupt ();     // preserve the message
      }
    }

    try
    {
      connection.close ();
      logger.info ("Connection closed");
    }
    catch (SQLException e)
    {
      logger.error ("Error closing connection", e);
    }
  }

  // ---------------------------------------------------------------------------------//
  private void process (DatabaseRequest request)
  // ---------------------------------------------------------------------------------//
  {
    switch (request.command)
    {
      case OPEN:
        if (schema.create ())                       // create if not already there
          request.result = Result.SUCCESS;
        break;

      case DROP:
        if (dropTables ())
          request.result = Result.SUCCESS;
        break;

      case CREATE:
        if (dropTables () && schema.create ())
          request.result = Result.SUCCESS;
        break;

      case CLOSE:
        cancelled = true;
        request.result = Result.SUCCESS;
        break;

      default:
        logger.warn ("Unnown database request: {}", request);
        break;
    }
  }

  // ---------------------------------------------------------------------------------//
  private void process (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Optional<Dataset> optDataset = datasets.find (request.datasetName);

    switch (request.command)
    {
      case ADD:
        if (!optDataset.isPresent () && datasets.insert (request.dataset))
          request.result = Result.SUCCESS;
        break;

      case UPDATE:
        if (optDataset.isPresent ())
        {
          if (updateDataset (request))
            request.result = Result.SUCCESS;
        }
        else
        {
          if (datasets.insert (request.dataset))
            request.result = Result.SUCCESS;
        }
        break;

      case MODIFY:
        if (optDataset.isPresent () && updateDataset (request))
          request.result = Result.SUCCESS;
        break;

      case DELETE:
        if (optDataset.isPresent () && datasets.delete (optDataset.get ()))
          request.result = Result.SUCCESS;
        break;

      case FIND:
        if (optDataset.isPresent ())
        {
          request.dataset = optDataset.get ();
          request.result = Result.SUCCESS;
        }
        break;

      case LIST:
        if (createDatasetList (request))
          request.result = Result.SUCCESS;
        break;

      default:
        logger.warn ("Unnown dataset request: {}", request);
        break;
    }
  }

  // ---------------------------------------------------------------------------------//
  private void process (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Optional<Member> optMember = null;
    if (request.command != LIST)
      optMember = members.find (request.member.getDataset (), request.member.getName ());

    switch (request.command)
    {
      case ADD:
        if (!optMember.isPresent ()
            && members.insert (request.datasetName, request.member))
          request.result = Result.SUCCESS;
        break;

      case UPDATE:
        if (optMember.isPresent ())
        {
          if (updateMember (request))
            request.result = Result.SUCCESS;
        }
        else
        {
          if (members.insert (request.datasetName, request.member))
            request.result = Result.SUCCESS;
        }
        break;

      case MODIFY:
        if (optMember.isPresent () && updateMember (request))
          request.result = Result.SUCCESS;
        break;

      case DELETE:
        if (optMember.isPresent () && members.delete (optMember.get ()))
          request.result = Result.SUCCESS;
        break;

      case FIND:
        if (optMember.isPresent ())
        {
          request.member = optMember.get ();
          request.result = Result.SUCCESS;
        }
        break;

      case LIST:
        if (createMemberList (request))
          request.result = Result.SUCCESS;
        break;

      default:
        logger.warn ("Unnown member request: {}", request);
        break;
    }
  }

  /*
   * Derrubar as tabelas limpa o cache junto, e so quando o drop deu certo - como antes. O
   * cache indexa o que o banco tinha; apagar um sem o outro deixaria os dois em desacordo.
   */
  // ---------------------------------------------------------------------------------//
  private boolean dropTables ()
  // ---------------------------------------------------------------------------------//
  {
    if (!schema.drop ())
      return false;

    cache.clear ();
    return true;
  }

  // ---------------------------------------------------------------------------------//
  private boolean createDatasetList (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    request.datasets = new ArrayList<> ();
    return datasets.list (request.datasetName, request.datasets);
  }

  // ---------------------------------------------------------------------------------//
  private boolean createMemberList (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    request.members = new ArrayList<> ();
    return members.list (request.datasetName, request.memberName, request.members);
  }

  /*
   * O UPDATE nao e uma gravacao direta. Ele procura o que ja esta la e pergunta se o que
   * chegou DIFERE: quando nao difere, sai devolvendo sucesso sem escrever e sem marcar
   * databaseUpdated - que e o campo que o QueuedDatasetStore devolve ao StoreListener.
   *
   * Quando difere, funde o novo no que estava e e o OBJETO FUNDIDO que vai para o banco e de
   * volta para a requisicao. Os campos que a requisicao nao declara sobrevivem.
   */
  // ---------------------------------------------------------------------------------//
  private boolean updateDataset (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Dataset dataset = request.dataset;
    Optional<Dataset> optDataset = datasets.find (dataset.getName ());
    if (optDataset.isPresent ())
    {
      Dataset currentDataset = optDataset.get ();
      if (!currentDataset.differsFrom (dataset))
        return true;

      currentDataset.merge (dataset);
      dataset = currentDataset;
      request.dataset = dataset;
    }
    else
      cache.put (dataset);

    if (!datasets.update (dataset))
      return false;

    request.databaseUpdated = true;
    cache.replaceDataset (dataset);

    return true;
  }

  // A mesma forma do updateDataset, com o merge proprio do Member.
  // ---------------------------------------------------------------------------------//
  private boolean updateMember (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Member member = request.member;
    Optional<Dataset> optDataset = datasets.find (member.getDataset ().getName ());
    Optional<Member> optMember = members.find (member.getDataset (), member.getName ());
    if (optMember.isPresent ())
    {
      Member currentMember = optMember.get ();
      if (!currentMember.differsFrom (member))
        return true;

      currentMember.merge (member);
      member = currentMember;
      request.member = member;

      cache.putMember (optDataset.get (), currentMember);
    }
    else
    {
      if (optDataset.isPresent ())
        cache.putMember (optDataset.get (), member);
      else
      {
        cache.put (member.getDataset ());
        cache.putMember (member.getDataset (), member);
      }
    }

    if (!members.update (member))
      return false;

    request.databaseUpdated = true;

    return true;
  }
}
