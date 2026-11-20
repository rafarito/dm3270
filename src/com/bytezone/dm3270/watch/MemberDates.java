package com.bytezone.dm3270.watch;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.datasets.Member;

// O primeiro trecho e convertido duas vezes ("Ext:" e "Size") para preservar os logs legados.
// -----------------------------------------------------------------------------------//
final class MemberDates implements MemberDetailFormat
// -----------------------------------------------------------------------------------//
{
  private final int[] tabs;

  // ---------------------------------------------------------------------------------//
  MemberDates (int[] tabs)
  // ---------------------------------------------------------------------------------//
  {
    this.tabs = tabs;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void read (DatasetSummary member, String details, Member delta)
  // ---------------------------------------------------------------------------------//
  {
    member.setCreated (details.substring (tabs[0], tabs[1]).trim ());
    member.setReferredDate (details.substring (tabs[1], tabs[2]).trim ());
    member.setReferredTime (details.substring (tabs[2], tabs[3]).trim ());
    member.setCatalog (details.substring (tabs[3]).trim ());
    member.setExtents (
        DatasetDetails.getInteger ("Ext:", details.substring (0, tabs[0]).trim ()));

    int size = DatasetDetails.getInteger ("Size", details.substring (0, tabs[0]).trim ());
    String created = details.substring (tabs[0], tabs[1]);
    String changed = details.substring (tabs[1], tabs[3]);
    String id = details.substring (tabs[3]).trim ();

    delta.setDates (created, changed);
    delta.setID (id);
    delta.setSize (size);
  }
}
