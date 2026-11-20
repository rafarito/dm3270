package com.bytezone.dm3270.watch;

// -----------------------------------------------------------------------------------//
final class MemberColumns
// -----------------------------------------------------------------------------------//
{
  // Lista do 3.1 e lista de 4 menus (mesmo em EDIT/BROWSE/VIEW, pois e tela distinta do 3.4).
  static final MemberColumns LIBRARY =
      new MemberColumns (new int[] { 12, 25, 38, 47 }, new int[] { 12, 21, 31, 43 });

  // A lista alcancada pelo 3.4 - EDIT, BROWSE, VIEW e DSLIST na lista de cinco menus.
  static final MemberColumns DATASET_LIST =
      new MemberColumns (new int[] { 9, 21, 33, 42 }, new int[] { 9, 17, 25, 36 });

  private final MemberDetailFormat dates;
  private final MemberDetailFormat statistics;

  // ---------------------------------------------------------------------------------//
  private MemberColumns (int[] dateTabs, int[] statisticsTabs)
  // ---------------------------------------------------------------------------------//
  {
    this.dates = new MemberDates (dateTabs);
    this.statistics = new MemberStatistics (statisticsTabs);
  }

  // ---------------------------------------------------------------------------------//
  MemberDetailFormat dates ()
  // ---------------------------------------------------------------------------------//
  {
    return dates;
  }

  // ---------------------------------------------------------------------------------//
  MemberDetailFormat statistics ()
  // ---------------------------------------------------------------------------------//
  {
    return statistics;
  }
}
