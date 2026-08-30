package com.bytezone.dm3270.database;

import java.util.ArrayList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.database.DatabaseRequest.Result;
import com.bytezone.dm3270.datasets.Dataset;

/*
 * Os seis comandos que operam sobre um dataset: ADD, UPDATE, MODIFY, DELETE, FIND e LIST.
 *
 * Aqui mora a regra, e no DatasetRepository mora o SQL. A diferenca fica visivel no UPDATE: o
 * repositorio sabe executar um update, e quem decide SE ha o que atualizar - e o que
 * exatamente gravar depois de fundir - e este metodo.
 *
 * A busca do dataset acontece UMA vez, antes do switch, e serve a todos os ramos. O UPDATE
 * procura de novo la dentro, o que e redundante e esta preservado: cada busca tambem escreve
 * no cache, e mudar a contagem mudaria o que fica guardado.
 *
 * O logger e o do DatabaseThread, e o aviso do ramo default mantem o "Unnown" de origem.
 */
// -----------------------------------------------------------------------------------//
final class DatasetCommands
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DatabaseThread.class);

  private final DatasetRepository datasets;
  private final DatasetCache cache;

  // ---------------------------------------------------------------------------------//
  DatasetCommands (DatasetRepository datasets, DatasetCache cache)
  // ---------------------------------------------------------------------------------//
  {
    this.datasets = datasets;
    this.cache = cache;
  }

  // ---------------------------------------------------------------------------------//
  void execute (DatasetRequest request)
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
          if (update (request))
            request.result = Result.SUCCESS;
        }
        else
        {
          if (datasets.insert (request.dataset))
            request.result = Result.SUCCESS;
        }
        break;

      case MODIFY:
        if (optDataset.isPresent () && update (request))
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
        if (list (request))
          request.result = Result.SUCCESS;
        break;

      default:
        logger.warn ("Unnown dataset request: {}", request);
        break;
    }
  }

  /*
   * O UPDATE nao e uma gravacao direta. Ele procura o que ja esta la e pergunta se o que
   * chegou DIFERE: quando nao difere, sai devolvendo sucesso sem escrever e sem marcar
   * databaseUpdated - que e o campo que o QueuedDatasetStore devolve ao StoreListener.
   *
   * Quando difere, funde o novo no que estava, e e o OBJETO FUNDIDO que vai para o banco e de
   * volta para a requisicao. Os campos que a requisicao nao declara sobrevivem.
   */
  // ---------------------------------------------------------------------------------//
  private boolean update (DatasetRequest request)
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

  // A lista e criada antes da consulta: numa falha no meio, quem pediu fica com o que deu.
  // ---------------------------------------------------------------------------------//
  private boolean list (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    request.datasets = new ArrayList<> ();
    return datasets.list (request.datasetName, request.datasets);
  }
}
