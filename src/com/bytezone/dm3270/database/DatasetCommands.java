package com.bytezone.dm3270.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.database.DatabaseRequest.Result;
import com.bytezone.dm3270.datasets.Dataset;

// Usa o logger de DatabaseThread para preservar o formato de saida do logback.
// A segunda busca em update() e intencional porque find() tambem atualiza o cache.
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
    Optional<Dataset> optDataset = datasets.find (request.getDatasetName ());

    switch (request.command)
    {
      case ADD:
        if (!optDataset.isPresent () && datasets.insert (request.getDataset ()))
          request.setResult (Result.SUCCESS);
        break;

      case UPDATE:
        if (optDataset.isPresent ())
        {
          if (update (request))
            request.setResult (Result.SUCCESS);
        }
        else
        {
          if (datasets.insert (request.getDataset ()))
            request.setResult (Result.SUCCESS);
        }
        break;

      case MODIFY:
        if (optDataset.isPresent () && update (request))
          request.setResult (Result.SUCCESS);
        break;

      case DELETE:
        if (optDataset.isPresent () && datasets.delete (optDataset.get ()))
          request.setResult (Result.SUCCESS);
        break;

      case FIND:
        if (optDataset.isPresent ())
        {
          request.setDataset (optDataset.get ());
          request.setResult (Result.SUCCESS);
        }
        break;

      case LIST:
        if (list (request))
          request.setResult (Result.SUCCESS);
        break;

      default:
        logger.warn ("Unnown dataset request: {}", request);
        break;
    }
  }

  // ---------------------------------------------------------------------------------//
  private boolean update (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    Dataset dataset = request.getDataset ();
    Optional<Dataset> optDataset = datasets.find (dataset.getName ());
    if (optDataset.isPresent ())
    {
      Dataset currentDataset = optDataset.get ();
      if (!currentDataset.differsFrom (dataset))
        return true;

      currentDataset.merge (dataset);
      dataset = currentDataset;
      request.setDataset (dataset);
    }
    else
      cache.put (dataset);

    if (!datasets.update (dataset))
      return false;

    request.markDatabaseUpdated ();
    cache.replaceDataset (dataset);

    return true;
  }

  // Associa a lista antes da consulta para manter itens ja lidos em caso de falha.
  // ---------------------------------------------------------------------------------//
  private boolean list (DatasetRequest request)
  // ---------------------------------------------------------------------------------//
  {
    List<Dataset> found = new ArrayList<> ();
    request.setDatasets (found);
    return datasets.list (request.getDatasetName (), found);
  }
}
