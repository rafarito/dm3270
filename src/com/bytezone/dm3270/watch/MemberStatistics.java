package com.bytezone.dm3270.watch;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.datasets.Member;

/*
 * O formato de estatisticas de edicao do campo de detalhes de um membro. Era o screenType2.
 *
 * Os mesmos quatro deslocamentos recortam outra coisa: tamanho, linhas iniciais, linhas
 * modificadas, a versao VV.MM e o id.
 *
 * Duas particularidades que a decomposicao preserva porque sao comportamento observavel, e nao
 * descuido de transcricao:
 *
 * - o summary nao tem campo para tamanho nem para id, entao este formato usa as colunas de
 *   catalogo e de extents para guarda-los. Os comentarios "(mis)use" sao do codigo original;
 * - o setSize completo so acontece quando a versao vem preenchida. Com VV.MM em branco, o
 *   tamanho, o init e o mod sao calculados e descartados - o Member fica com os valores
 *   anteriores, e so o id e gravado.
 */
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
