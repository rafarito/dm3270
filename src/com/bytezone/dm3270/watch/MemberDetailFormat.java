package com.bytezone.dm3270.watch;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.datasets.Member;

// Parser de detalhes de linha de membro; as posicoes de coluna vem de MemberColumns.
// -----------------------------------------------------------------------------------//
interface MemberDetailFormat
// -----------------------------------------------------------------------------------//
{
  // Preenche member (tabela do assistant) e delta (persistido por quem chama).
  void read (DatasetSummary member, String details, Member delta);
}
