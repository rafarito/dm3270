package com.bytezone.dm3270.database;

import static com.bytezone.dm3270.database.DatabaseRequest.Command.LIST;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.database.DatabaseRequest.Result;
import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.Member;

// Usa o logger de DatabaseThread para preservar o formato de saida do logback.
// -----------------------------------------------------------------------------------//
final class MemberCommands
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private final MemberRepository members;
  private final DatasetRepository datasets;
  private final DatasetCache cache;

  // ---------------------------------------------------------------------------------//
  MemberCommands (MemberRepository members, DatasetRepository datasets, DatasetCache cache)
  // ---------------------------------------------------------------------------------//
  {
    this.members = members;
    this.datasets = datasets;
    this.cache = cache;
  }

  // ---------------------------------------------------------------------------------//
  void execute (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Optional<Member> optMember = null;
    if (request.command != LIST)
      optMember = members.find (request.getMember ().getDataset (),
          request.getMember ().getName ());

    switch (request.command)
    {
      case ADD:
        if (!optMember.isPresent ()
            && members.insert (request.getDatasetName (), request.getMember ()))
          request.setResult (Result.SUCCESS);
        break;

      case UPDATE:
        if (optMember.isPresent ())
        {
          if (update (request))
            request.setResult (Result.SUCCESS);
        }
        else
        {
          if (members.insert (request.getDatasetName (), request.getMember ()))
            request.setResult (Result.SUCCESS);
        }
        break;

      case MODIFY:
        if (optMember.isPresent () && update (request))
          request.setResult (Result.SUCCESS);
        break;

      case DELETE:
        if (optMember.isPresent () && members.delete (optMember.get ()))
          request.setResult (Result.SUCCESS);
        break;

      case FIND:
        if (optMember.isPresent ())
        {
          request.setMember (optMember.get ());
          request.setResult (Result.SUCCESS);
        }
        break;

      case LIST:
        if (list (request))
          request.setResult (Result.SUCCESS);
        break;

      default:
        logger.warn ("Unnown member request: {}", request);
        break;
    }
  }

  // ---------------------------------------------------------------------------------//
  private boolean update (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Member member = request.getMember ();
    Optional<Dataset> optDataset = datasets.find (member.getDataset ().getName ());
    Optional<Member> optMember = members.find (member.getDataset (), member.getName ());
    if (optMember.isPresent ())
    {
      Member currentMember = optMember.get ();
      if (!currentMember.differsFrom (member))
        return true;

      currentMember.merge (member);
      member = currentMember;
      request.setMember (member);

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

    request.markDatabaseUpdated ();

    return true;
  }

  // ---------------------------------------------------------------------------------//
  private boolean list (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    List<Member> found = new ArrayList<> ();
    request.setMembers (found);
    return members.list (request.getDatasetName (), request.getMemberName (), found);
  }
}
