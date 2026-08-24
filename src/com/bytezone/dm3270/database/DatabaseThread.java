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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;

import com.bytezone.dm3270.database.DatabaseRequest.Result;

// -----------------------------------------------------------------------------------//
public class DatabaseThread extends Thread
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private static final String INSERT_DATASET =
      "insert into DATASETS (VOLUME, DEVICE, CATALOG, "
          + "TRACKS, CYLINDERS, PERCENT, EXTENTS, DSORG, RECFM, LRECL, BLKSIZE,"
          + "CREATED, EXPIRES, REFERRED, NAME) "
          + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String INSERT_MEMBER =
      "insert into MEMBERS (ID, SIZE, INIT, MOD, VV, MM, CREATED, "
          + "CHANGED, DATASET, NAME) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String UPDATE_DATASET = "update DATASETS set VOLUME=?, DEVICE=?, "
      + "CATALOG=?, TRACKS=?, CYLINDERS=?, PERCENT=?, EXTENTS=?, DSORG=?, RECFM=?, "
      + "LRECL=?, BLKSIZE=?, CREATED=?, EXPIRES=?, REFERRED=? where NAME=?";

  private static final String UPDATE_MEMBER =
      "update MEMBERS set ID=?, SIZE=?, INIT=?, MOD=?, VV=?, MM=?, CREATED=?, "
          + "CHANGED=? where DATASET=? and NAME=?";

  private Connection connection;
  private BlockingQueue<DatabaseRequest> queue;
  private boolean cancelled;
  private final String databaseName;

  private final Map<String, CacheEntry> cache = new TreeMap<> ();

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
        if (create (request))                       // create if not already there
          request.result = Result.SUCCESS;
        break;

      case DROP:
        if (drop (request))
          request.result = Result.SUCCESS;
        break;

      case CREATE:
        if (drop (request) && create (request))
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
    Optional<Dataset> optDataset = findDataset (request.datasetName);

    switch (request.command)
    {
      case ADD:
        if (!optDataset.isPresent () && insertDataset (request.dataset))
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
          if (insertDataset (request.dataset))
            request.result = Result.SUCCESS;
        }
        break;

      case MODIFY:
        if (optDataset.isPresent () && updateDataset (request))
          request.result = Result.SUCCESS;
        break;

      case DELETE:
        if (optDataset.isPresent () && deleteDataset (optDataset.get ()))
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
      optMember = findMember (request.member.getDataset (), request.member.getName ());

    switch (request.command)
    {
      case ADD:
        if (!optMember.isPresent () && insertMember (request))
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
          if (insertMember (request))
            request.result = Result.SUCCESS;
        }
        break;

      case MODIFY:
        if (optMember.isPresent () && updateMember (request))
          request.result = Result.SUCCESS;
        break;

      case DELETE:
        if (optMember.isPresent () && deleteMember (optMember.get ()))
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

  // ---------------------------------------------------------------------------------//
  private boolean drop (DatabaseRequest request)
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

      cache.clear ();

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error dropping tables", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  private boolean create (DatabaseRequest request)
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
  private boolean createDatasetList (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    request.datasets = new ArrayList<> ();
    try
    {
      Statement stmt = connection.createStatement ();

      // Convencao do filtro: vazio ou "*" lista tudo, "PREFIXO*" lista a faixa, e um
      // nome sem curinga procura aquele dataset exato. O nome vazio ia para o ramo do
      // nome exato e devolvia sempre lista vazia.
      String query = "select * from DATASETS ";
      int pos = request.datasetName.isEmpty () ? 0 : request.datasetName.indexOf ('*');

      if (pos < 0)
        query += "where NAME='" + request.datasetName + "'";
      else if (pos > 0)
      {
        String from = request.datasetName.substring (0, pos);
        String to = from + "Z";
        query += "where NAME>='" + from + "' and NAME<='" + to + "'";
      }

      ResultSet rs = stmt.executeQuery (query);
      while (rs.next ())
      {
        Dataset dataset = DatasetMapper.read (rs);
        request.datasets.add (dataset);
        CacheEntry cacheEntry = cache.get (dataset.getName ());
        if (cacheEntry == null)
          cache.put (dataset.getName (), new CacheEntry (dataset));
        else
          cacheEntry.replace (dataset);       // is this necessary?
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
  private boolean createMemberList (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    request.members = new ArrayList<> ();
    Optional<Dataset> optDataset = findDataset (request.datasetName);
    if (!optDataset.isPresent ())
      return false;
    Dataset dataset = optDataset.get ();
    CacheEntry cacheEntry = cache.get (dataset.getName ());

    try
    {
      Statement stmt = connection.createStatement ();

      String query = "select * from MEMBERS where DATASET='" + request.datasetName + "'";
      int pos = request.memberName.indexOf ('*');

      if (pos > 0)
      {
        String from = request.memberName.substring (0, pos);
        String to = from + "Z";
        query += "where NAME>='" + from + "' and NAME<='" + to + "'";
      }

      ResultSet rs = stmt.executeQuery (query);
      while (rs.next ())
      {
        Member member = MemberMapper.read (rs, dataset);
        request.members.add (member);
        cacheEntry.addMember (member);
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
  private Optional<Dataset> findDataset (String datasetName)
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
        CacheEntry cacheEntry = cache.get (dataset.getName ());
        if (cacheEntry == null)
          cache.put (dataset.getName (), new CacheEntry (dataset));
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
  private Optional<Member> findMember (Dataset dataset, String memberName)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      Statement stmt = connection.createStatement ();
      ResultSet rs = stmt.executeQuery ("select * from MEMBERS where DATASET='"
          + dataset.getName () + "' and NAME='" + memberName + "'");

      if (rs.next ())
      {
        Optional<Dataset> optDataset = findDataset (dataset.getName ());
        if (optDataset.isPresent ())
          dataset = optDataset.get ();        // replace the parameter we were given

        CacheEntry cacheEntry = cache.get (dataset.getName ());

        Member member = MemberMapper.read (rs, dataset);
        cacheEntry.putMember (member);
        return Optional.of (member);
      }
    }
    catch (SQLException e)
    {
      logger.error ("Error finding member", e);
    }
    return Optional.empty ();
  }

  // ---------------------------------------------------------------------------------//
  private boolean updateDataset (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Dataset dataset = request.dataset;
    Optional<Dataset> optDataset = findDataset (dataset.getName ());
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
    {
      CacheEntry cacheEntry = new CacheEntry (dataset);
      cache.put (dataset.getName (), cacheEntry);
    }

    try
    {
      logger.info ("Dataset modified:");
      logger.info ("{}", dataset);

      PreparedStatement ps3 = connection.prepareStatement (UPDATE_DATASET);

      DatasetMapper.bind (ps3, dataset);
      ps3.executeUpdate ();
      ps3.close ();
      request.databaseUpdated = true;

      CacheEntry cacheEntry = cache.get (dataset.getName ());
      cacheEntry.dataset = dataset;

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error updating dataset", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  private boolean updateMember (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Member member = request.member;
    Optional<Dataset> optDataset = findDataset (member.getDataset ().getName ());
    Optional<Member> optMember = findMember (member.getDataset (), member.getName ());
    if (optMember.isPresent ())
    {
      Member currentMember = optMember.get ();
      if (!currentMember.differsFrom (member))
        return true;

      currentMember.merge (member);
      member = currentMember;
      request.member = member;

      Dataset dataset = optDataset.get ();
      CacheEntry cacheEntry = cache.get (dataset.getName ());
      cacheEntry.putMember (currentMember);
    }
    else
    {
      if (optDataset.isPresent ())
      {
        Dataset dataset = optDataset.get ();
        CacheEntry cacheEntry = cache.get (dataset.getName ());
        cacheEntry.putMember (member);
      }
      else
      {
        CacheEntry cacheEntry = new CacheEntry (member.getDataset ());
        cache.put (member.getDataset ().getName (), cacheEntry);
        cacheEntry.putMember (member);
      }
    }

    try
    {
      logger.info ("Member modified: {}({})", member.getDataset ().getName (), member.getName ());
      logger.info ("{}", member);

      PreparedStatement ps4 = connection.prepareStatement (UPDATE_MEMBER);

      MemberMapper.bind (ps4, member);
      ps4.executeUpdate ();
      ps4.close ();
      request.databaseUpdated = true;

      return true;
    }
    catch (SQLException e)
    {
      logger.error ("Error updating member", e);
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  private boolean deleteDataset (Dataset dataset)
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

  // ---------------------------------------------------------------------------------//
  private boolean deleteMember (Member member)
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

  // ---------------------------------------------------------------------------------//
  private boolean insertDataset (Dataset dataset)
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
  private boolean insertMember (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Optional<Dataset> optDataset = findDataset (request.datasetName);
    if (optDataset.isPresent ())
    {
      Dataset dataset = optDataset.get ();
      if (dataset.getDsorg () == null)
      {
        dataset.markPartitioned ();
        String cmd = "update DATASETS set DSORG='PO' where NAME='" + dataset.getName () + "'";
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
    }
    else
    {
      Dataset dataset = new Dataset (request.datasetName);
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

    Member member = request.member;
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
}
