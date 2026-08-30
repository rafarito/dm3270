package com.bytezone.dm3270.watch;

/*
 * Em que colunas os detalhes de um membro sao cortados.
 *
 * Sao dois conjuntos por modo de tela, e nao um: o corte muda conforme o formato que a linha
 * de titulos anunciar. Andam juntos porque a tela decide na ordem inversa da que parece - o
 * modo fixa as colunas primeiro, e so depois os titulos escolhem entre datas e estatisticas.
 *
 * Os dois conjuntos ficam aqui porque as DUAS listas de membro os usavam: checkMemberList1
 * declarava os quatro vetores e checkMemberList2 declarava de novo, copiados, os dois de
 * LIBRARY. Era a mesma duplicacao que os layouts de lista de dataset tinham, em ponto menor.
 *
 * Os numeros sao os do codigo antigo, um por um.
 */
// -----------------------------------------------------------------------------------//
final class MemberColumns
// -----------------------------------------------------------------------------------//
{
  /*
   * A lista de membros de um PDS aberta pelo 3.1, e tambem a lista de quatro menus - que usa
   * este conjunto qualquer que seja o modo que ela anuncie, inclusive EDIT. Nao e engano de
   * transcricao: sao telas diferentes com colunas diferentes para o mesmo nome de modo.
   */
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
