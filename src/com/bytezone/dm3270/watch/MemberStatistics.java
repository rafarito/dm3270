package com.bytezone.dm3270.watch;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.datasets.Member;

// Sem VV.MM, delta.setSize() e ignorado (apenas member.setExtents e delta.setID gravam).
// -----------------------------------------------------------------------------------//
final class MemberStatistics implements MemberDetailFormat
// -----------------------------------------------------------------------------------//
{
  private final int[] tabs;

  // ---------------------------------------------------------------------------------//
  MemberStatistics (int[] tabs)
  // ---------------------------------------------------------------------------------//
  {
    this.tabs = tabs;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void read (DatasetSummary member, String details, Member delta)
  // ---------------------------------------------------------------------------------//
  {
    String vvmm = details.substring (tabs[2], tabs[3]).trim ();
    String id = details.substring (tabs[3]).trim ();

    int size = DatasetDetails.getInteger ("Size", details.substring (0, tabs[0]).trim ());
    int init =
        DatasetDetails.getInteger ("Init", details.substring (tabs[0], tabs[1]).trim ());
    int mod = DatasetDetails.getInteger ("Mod", details.substring (tabs[1], tabs[2]).trim ());

    if (!vvmm.isEmpty ())
    {
      int vv = DatasetDetails.getInteger ("VV", vvmm.substring (0, 2));
      int mm = DatasetDetails.getInteger ("MM", vvmm.substring (3));
      delta.setSize (size, init, mod, vv, mm);
    }

    member.setCatalog (id);               // (mis)use the catalog column
    member.setExtents (size);             // (mis)use the extents column

    delta.setID (id);
  }
}
