package com.bytezone.dm3270.database;

import static com.bytezone.dm3270.database.DatabaseRequest.Command.LIST;

import java.util.ArrayList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.database.DatabaseRequest.Result;
import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.Member;

/*
 * Os seis comandos que operam sobre um membro. A forma e a mesma do DatasetCommands, e as duas
 * classes parecem gemeas - mas nao sao, e a diferenca esta logo na primeira linha.
 *
 * O DatasetCommands procura o dataset SEMPRE, antes do switch. Aqui a busca do membro e pulada
 * quando o comando e LIST, e a variavel fica nula: o ramo do LIST nao a toca, e qualquer ramo
 * novo que a tocasse estouraria. Foi assim que o codigo chegou aqui, e esta preservado.
 *
 * O logger e o do DatabaseThread, e o aviso do ramo default mantem o "Unnown" de origem.
 */
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
          if (update (request))
            request.result = Result.SUCCESS;
        }
        else
        {
          if (members.insert (request.datasetName, request.member))
            request.result = Result.SUCCESS;
        }
        break;

      case MODIFY:
        if (optMember.isPresent () && update (request))
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
        if (list (request))
          request.result = Result.SUCCESS;
        break;

      default:
        logger.warn ("Unnown member request: {}", request);
        break;
    }
  }

  /*
   * A mesma forma do update de dataset, com o merge proprio do Member - e com uma diferenca no
   * fim: aqui nao ha replaceDataset depois de gravar, porque o que mudou foi o membro.
   *
   * O dataset dono e procurado mesmo quando o membro nao existe, porque e ele que diz em qual
   * entrada do cache o membro entra.
   */
  // ---------------------------------------------------------------------------------//
  private boolean update (MemberRequest request)
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

  // ---------------------------------------------------------------------------------//
  private boolean list (MemberRequest request)
  // ---------------------------------------------------------------------------------//
  {
    request.members = new ArrayList<> ();
    return members.list (request.datasetName, request.memberName, request.members);
  }
}
