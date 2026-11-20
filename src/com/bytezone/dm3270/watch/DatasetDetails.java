package com.bytezone.dm3270.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;

// Usa o logger de ScreenWatcher para preservar o nome nos logs de parse.
// -----------------------------------------------------------------------------------//
final class DatasetDetails
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (ScreenWatcher.class);

  // ---------------------------------------------------------------------------------//
  private DatasetDetails ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  static void setSpace (DatasetSummary dataset, String details, int t1, int t2, int t3)
  // ---------------------------------------------------------------------------------//
  {
    if (details.trim ().isEmpty ())
      return;

    if (details.length () >= t1)
      dataset.setTracks (getInteger ("tracks", details.substring (0, t1).trim ()));
    if (details.length () >= t2)
      dataset.setPercentUsed (getInteger ("pct", details.substring (t1, t2).trim ()));
    if (details.length () >= t3)
      dataset.setExtents (getInteger ("ext", details.substring (t2, t3).trim ()));
    if (details.length () > t3)
      dataset.setDevice (details.substring (t3).trim ());
  }

  // ---------------------------------------------------------------------------------//
  static void setDisposition (DatasetSummary dataset, String details, int t1, int t2, int t3)
  // ---------------------------------------------------------------------------------//
  {
    if (details.trim ().isEmpty ())
      return;

    if (details.length () >= t1)
      dataset.setDsorg (details.substring (0, t1).trim ());
    if (details.length () >= t2)
      dataset.setRecfm (details.substring (t1, t2).trim ());
    if (details.length () >= t3)
      dataset.setLrecl (getInteger ("lrecl", details.substring (t2, t3).trim ()));
    if (details.length () > t3)
      dataset.setBlksize (getInteger ("blksize", details.substring (t3).trim ()));
  }

  // summary preserva as datas brutas da tela; delta converte e deixa null se invalidas.
  // ---------------------------------------------------------------------------------//
  static void setDates (DatasetSummary dataset, String details, Dataset ds)
  // ---------------------------------------------------------------------------------//
  {
    if (details.trim ().isEmpty ())
      return;

    String created = details.substring (0, 11).trim ();
    String expires = details.substring (11, 22).trim ();
    String referred = details.substring (22).trim ();

    dataset.setCreated (created);
    dataset.setExpires (expires);
    dataset.setReferredDate (referred);

    ds.setDates (created, expires, referred);
  }

  // ---------------------------------------------------------------------------------//
  static int getInteger (String id, String value)
  // ---------------------------------------------------------------------------------//
  {
    if (value == null || value.isEmpty () || value.equals ("?"))
      return 0;

    try
    {
      return Integer.parseInt (value);
    }
    catch (NumberFormatException e)
    {
      logger.error ("ParseInt error with {}: [{}]", id, value, e);
      return 0;
    }
  }
}
