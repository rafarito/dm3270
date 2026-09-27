package com.bytezone.dm3270.database;

import com.bytezone.dm3270.datasets.Dataset;

import java.util.List;

// -----------------------------------------------------------------------------------//
public class DatasetRequest extends DatabaseRequest
// -----------------------------------------------------------------------------------//
{
  // O nome nunca muda depois do construtor. O dataset e a lista sao a resposta do worker.
  private Dataset dataset;
  private final String datasetName;
  private List<Dataset> datasets;

  // ---------------------------------------------------------------------------------//
  public DatasetRequest (Initiator initiator, Command command, String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    super (initiator, command);

    this.datasetName = datasetName;
  }

  // ---------------------------------------------------------------------------------//
  public DatasetRequest (Initiator initiator, Command command, Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    super (initiator, command);

    this.dataset = dataset;
    this.datasetName = dataset.getName ();
  }

  // ---------------------------------------------------------------------------------//
  public Dataset getDataset ()
  // ---------------------------------------------------------------------------------//
  {
    return dataset;
  }

  // ---------------------------------------------------------------------------------//
  void setDataset (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    this.dataset = dataset;
  }

  // ---------------------------------------------------------------------------------//
  public String getDatasetName ()
  // ---------------------------------------------------------------------------------//
  {
    return datasetName;
  }

  // ---------------------------------------------------------------------------------//
  public List<Dataset> getDatasets ()
  // ---------------------------------------------------------------------------------//
  {
    return datasets;
  }

  // ---------------------------------------------------------------------------------//
  void setDatasets (List<Dataset> datasets)
  // ---------------------------------------------------------------------------------//
  {
    this.datasets = datasets;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();

    text.append (super.toString ());
    text.append (String.format ("Dataset ....... %s%n", datasetName));

    return text.toString ();
  }
}
