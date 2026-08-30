package com.bytezone.dm3270.watch;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.datasets.Member;

/*
 * O formato de datas do campo de detalhes de um membro. Era o screenType1.
 *
 * Quatro deslocamentos recortam cinco pedacos: o que vem antes do primeiro, os tres do meio e
 * o que sobra depois do ultimo - tamanho, criacao, alteracao, hora e id.
 *
 * O primeiro pedaco e lido DUAS vezes, com ids de log diferentes: uma como "Ext:", que vai
 * para os extents do summary, e outra como "Size", que vai para o tamanho do Member. As duas
 * colunas terminam com o mesmo numero, e um valor que nao converta produz DUAS linhas de log,
 * nao uma. Ha teste dedicado a isso, e a duplicidade e preservada de proposito.
 */
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
