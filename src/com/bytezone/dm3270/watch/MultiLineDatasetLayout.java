package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

// -----------------------------------------------------------------------------------//
abstract class MultiLineDatasetLayout implements DatasetListLayout
// -----------------------------------------------------------------------------------//
{
  private static final int TRACKS = 6;
  private static final int PERCENT_USED = 10;
  private static final int EXTENTS = 14;

  private static final int DSORG = 5;
  private static final int RECFM = 10;
  private static final int LRECL = 16;

  // ---------------------------------------------------------------------------------//
  final void readDetails (DatasetSummary summary, List<Field> rowFields, Dataset delta)
  // ---------------------------------------------------------------------------------//
  {
    DatasetDetails.setSpace (summary, rowFields.get (3).getText (), TRACKS, PERCENT_USED,
        EXTENTS);
    DatasetDetails.setDisposition (summary, rowFields.get (4).getText (), DSORG, RECFM, LRECL);
    DatasetDetails.setDates (summary, rowFields.get (5).getText (), delta);

    delta.setSpace (summary.getTracks (), summary.getCylinders (), summary.getExtents (),
        summary.getPercentUsed ());
    delta.setDisposition (summary.getDsorg (), summary.getRecfm (), summary.getLrecl (),
        summary.getBlksize ());
  }
}
