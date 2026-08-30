package com.bytezone.dm3270.watch;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.datasets.Member;

/*
 * Como o campo de detalhes de uma linha de membro e cortado.
 *
 * Sao dois formatos, e a linha de titulos da tela escolhe qual: um traz datas - criacao,
 * alteracao, hora e id - e o outro traz estatisticas de edicao - tamanho, linhas iniciais,
 * linhas modificadas e a versao VV.MM.
 *
 * As colunas do corte NAO fazem parte do formato: elas vem do modo da tela, e o mesmo formato
 * e lido em colunas diferentes conforme se chegou ali pelo 3.1 ou pelo 3.4. Por isso a
 * instancia recebe os deslocamentos em vez de declara-los - quem os declara e MemberColumns.
 */
// -----------------------------------------------------------------------------------//
interface MemberDetailFormat
// -----------------------------------------------------------------------------------//
{
  /*
   * O member e o que a tabela do assistant mostra e o delta e o que vai para o DatasetStore.
   * Quem chama e que grava, como na lista de dataset.
   */
  void read (DatasetSummary member, String details, Member delta);
}
