package com.bytezone.dm3270.database;

import java.util.List;

// -----------------------------------------------------------------------------------//
public class DatasetRequest extends DatabaseRequest
// -----------------------------------------------------------------------------------//
{
  public Dataset dataset;
  public String datasetName;
  public List<Dataset> datasets;

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
