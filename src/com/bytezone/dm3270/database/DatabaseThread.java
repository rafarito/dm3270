package com.bytezone.dm3270.database;

import static com.bytezone.dm3270.database.DatabaseRequest.Command.LIST;

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

    try
    {
      Class.forName ("org.sqlite.JDBC");     // add sqlite JDBC Driver to DriverManager
      Path path = Paths.get (System.getProperty ("user.home"), "dm3270", "databases",
          databaseName);
      String connectionName = "jdbc:sqlite:" + path.toString ();
      connection = DriverManager.getConnection (connectionName);
      connection.setAutoCommit (true);
      this.queue = queue;
    }
    catch (ClassNotFoundException | SQLException e)
    {
      e.printStackTrace ();
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
        System.out.println ("interrupted");
        cancelled = true;
        Thread.currentThread ().interrupt ();     // preserve the message
      }
    }

    try
    {
      connection.close ();
      System.out.println ("Connection closed");
    }
    catch (SQLException e)
    {
      e.printStackTrace ();
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
        System.out.printf ("Unnown database request: %s%n", request);
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
        System.out.printf ("Unnown dataset request: %s%n", request);
        break;
    }
  }

  // ---------------------------------------------------------------------------------//
  private void process (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Optional<Member> optMember = null;
    if (request.command != LIST)
      optMember = findMember (request.member.dataset, request.member.name);

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

      case LIST:
        if (createMemberList (request))
          request.result = Result.SUCCESS;
        break;

      default:
        System.out.printf ("Unnown member request: %s%n", request);
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
      System.err.println (e.getClass ().getName () + ": " + e.getMessage ());
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

      String query = "select * from DATASETS ";
      int pos = request.datasetName.indexOf ('*');

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
        Dataset dataset = createDataset (rs);
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
      System.err.println (e.getClass ().getName () + ": " + e.getMessage ());
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
        Member member = createMember (rs, dataset);
        request.members.add (member);
        cacheEntry.addMember (member);
      }

      return true;
    }
    catch (SQLException e)
    {
      System.err.println (e.getClass ().getName () + ": " + e.getMessage ());
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
        Dataset dataset = createDataset (rs);
        CacheEntry cacheEntry = cache.get (dataset.name);
        if (cacheEntry == null)
          cache.put (dataset.name, new CacheEntry (dataset));
        return Optional.of (dataset);
      }
    }
    catch (SQLException e)
    {
      System.err.println (e.getClass ().getName () + ": " + e.getMessage ());
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

        CacheEntry cacheEntry = cache.get (dataset.name);

        Member member = createMember (rs, dataset);
        cacheEntry.putMember (member);
        return Optional.of (member);
      }
    }
    catch (SQLException e)
    {
      System.err.println (e.getClass ().getName () + ": " + e.getMessage ());
    }
    return Optional.empty ();
  }

  // ---------------------------------------------------------------------------------//
  private boolean updateDataset (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Dataset dataset = request.dataset;
    Optional<Dataset> optDataset = findDataset (dataset.name);
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
      cache.put (dataset.name, cacheEntry);
    }

    try
    {
      System.out.println ("Dataset modified:");
      System.out.println (dataset);

      PreparedStatement ps3 = connection.prepareStatement (UPDATE_DATASET);

      setDatasetStatement (ps3, dataset);
      ps3.executeUpdate ();
      ps3.close ();
      request.databaseUpdated = true;

      CacheEntry cacheEntry = cache.get (dataset.name);
      cacheEntry.dataset = dataset;

      return true;
    }
    catch (SQLException e)
    {
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  private boolean updateMember (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Member member = request.member;
    Optional<Dataset> optDataset = findDataset (member.dataset.name);
    Optional<Member> optMember = findMember (member.dataset, member.name);
    if (optMember.isPresent ())
    {
      Member currentMember = optMember.get ();
      if (!currentMember.differsFrom (member))
        return true;

      currentMember.merge (member);
      member = currentMember;
      request.member = member;

      Dataset dataset = optDataset.get ();
      CacheEntry cacheEntry = cache.get (dataset.name);
      cacheEntry.putMember (currentMember);
    }
    else
    {
      if (optDataset.isPresent ())
      {
        Dataset dataset = optDataset.get ();
        CacheEntry cacheEntry = cache.get (dataset.name);
        cacheEntry.putMember (member);
      }
      else
      {
        CacheEntry cacheEntry = new CacheEntry (member.dataset);
        cache.put (member.dataset.name, cacheEntry);
        cacheEntry.putMember (member);
      }
    }

    try
    {
      System.out.printf ("Member modified: %s(%s)%n", member.dataset.name, member.name);
      System.out.println (member);

      PreparedStatement ps4 = connection.prepareStatement (UPDATE_MEMBER);

      setMemberStatement (ps4, member);
      ps4.executeUpdate ();
      ps4.close ();
      request.databaseUpdated = true;

      return true;
    }
    catch (SQLException e)
    {
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
      try
      {
        connection.rollback ();
      }
      catch (SQLException e1)
      {
        e1.printStackTrace ();
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
      stmt.executeUpdate ("delete from MEMEBERS where DATASET='"
          + member.dataset.getName () + "' and NAME='" + member.getName () + "'");
      stmt.close ();
      return true;
    }
    catch (SQLException e)
    {
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

      setDatasetStatement (ps1, dataset);
      ps1.executeUpdate ();
      ps1.close ();

      return true;
    }
    catch (SQLException e)
    {
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
      if (dataset.dsorg == null)
      {
        dataset.dsorg = "PO";
        String cmd = "update DATASETS set DSORG='PO' where NAME='" + dataset.name + "'";
        try
        {
          Statement stmt = connection.createStatement ();
          stmt.executeUpdate (cmd);
          stmt.close ();
        }
        catch (SQLException e)
        {
          e.printStackTrace ();
        }
      }
    }
    else
    {
      Dataset dataset = new Dataset (request.datasetName);
      dataset.dsorg = "PO";
      String cmd =
          "insert into DATASETS (NAME,DSORG) values ('" + dataset.name + "', 'PO')";
      try
      {
        Statement stmt = connection.createStatement ();
        stmt.executeUpdate (cmd);
        stmt.close ();
      }
      catch (SQLException e)
      {
        e.printStackTrace ();
      }
    }

    Member member = request.member;
    try
    {
      PreparedStatement ps2 = connection.prepareStatement (INSERT_MEMBER);

      setMemberStatement (ps2, member);
      ps2.executeUpdate ();
      ps2.close ();

      return true;
    }
    catch (SQLException e)
    {
      return false;
    }
  }

  // ---------------------------------------------------------------------------------//
  private void setDatasetStatement (PreparedStatement ps, Dataset dataset)
      throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    ps.setString (1, dataset.volume);
    ps.setString (2, dataset.device);
    ps.setString (3, dataset.catalog);
    ps.setInt (4, dataset.tracks);
    ps.setInt (5, dataset.cylinders);
    ps.setInt (6, dataset.percent);
    ps.setInt (7, dataset.extents);
    ps.setString (8, dataset.dsorg);
    ps.setString (9, dataset.recfm);
    ps.setInt (10, dataset.lrecl);
    ps.setInt (11, dataset.blksize);
    ps.setDate (12, dataset.createdSQL == null ? null : dataset.createdSQL);
    ps.setDate (13, dataset.expiresSQL == null ? null : dataset.expiresSQL);
    ps.setDate (14, dataset.referredSQL == null ? null : dataset.referredSQL);
    ps.setString (15, dataset.getName ());
  }

  // ---------------------------------------------------------------------------------//
  private void setMemberStatement (PreparedStatement ps, Member member)
      throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    ps.setString (1, member.id);
    ps.setInt (2, member.size);
    ps.setInt (3, member.init);
    ps.setInt (4, member.mod);
    ps.setInt (5, member.vv);
    ps.setInt (6, member.mm);
    ps.setDate (7, member.createdSQL == null ? null : member.createdSQL);
    ps.setDate (8, member.changedSQL == null ? null : member.changedSQL);
    ps.setString (9, member.dataset.getName ());
    ps.setString (10, member.getName ());
  }

  // ---------------------------------------------------------------------------------//
  private Dataset createDataset (ResultSet rs) throws SQLException
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

  // ---------------------------------------------------------------------------------//
  private Member createMember (ResultSet rs, Dataset dataset) throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    Member member = new Member (dataset, rs.getString ("name"));
    member.setID (rs.getString ("id"));
    member.setSize (rs.getInt ("size"), rs.getInt ("init"), rs.getInt ("mod"),
        rs.getInt ("vv"), rs.getInt ("mm"));
    member.setDates (rs.getDate ("created"), rs.getDate ("changed"));
    return member;
  }
}
