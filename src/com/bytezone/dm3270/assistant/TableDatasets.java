package com.bytezone.dm3270.assistant;

import java.util.IdentityHashMap;
import java.util.Map;

import com.bytezone.dm3270.datasets.DatasetSummary;

// Reaproveita a TableDataset por identidade do summary para atualizar a TableView in-place
// sem sobrescrever linhas antigas quando o ScreenWatcher e recriado.
// -----------------------------------------------------------------------------------//
public class TableDatasets
// -----------------------------------------------------------------------------------//
{
  private final Map<DatasetSummary, TableDataset> rows = new IdentityHashMap<> ();

  // ---------------------------------------------------------------------------------//
  public TableDataset rowFor (DatasetSummary summary)
  // ---------------------------------------------------------------------------------//
  {
    TableDataset row = rows.get (summary);

    if (row == null)
    {
      row = new TableDataset (summary.getDatasetName ());
      rows.put (summary, row);
    }

    copy (summary, row);

    return row;
  }

  // O nome ja e fixado na criacao (chave no acumulador do ScreenWatcher).
  // ---------------------------------------------------------------------------------//
  private static void copy (DatasetSummary summary, TableDataset row)
  // ---------------------------------------------------------------------------------//
  {
    row.setVolume (summary.getVolume ());
    row.setDevice (summary.getDevice ());
    row.setCatalog (summary.getCatalog ());
    row.setCreated (summary.getCreated ());
    row.setExpires (summary.getExpires ());
    row.setReferredDate (summary.getReferredDate ());
    row.setReferredTime (summary.getReferredTime ());
    row.setDsorg (summary.getDsorg ());
    row.setRecfm (summary.getRecfm ());
    row.setTracks (summary.getTracks ());
    row.setCylinders (summary.getCylinders ());
    row.setExtents (summary.getExtents ());
    row.setPercentUsed (summary.getPercentUsed ());
    row.setLrecl (summary.getLrecl ());
    row.setBlksize (summary.getBlksize ());
  }
}
