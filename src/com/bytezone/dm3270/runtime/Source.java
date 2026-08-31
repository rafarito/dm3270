package com.bytezone.dm3270.runtime;

/*
 * De qual lado da conversa veio um buffer.
 *
 * Era um enum aninhado dentro de streams.TelnetSocket - a classe que abre a conexao - e por
 * isso o pacote session inteiro importava streams so para dizer "este registro veio do
 * cliente". Nao ha nada de socket num lado de conversa: e um dado, gravado em cada linha do
 * arquivo de replay e mostrado numa coluna da tabela.
 *
 * Aqui, num pacote sem dependencia nenhuma, ele pode ser lido pelas duas camadas sem que
 * nenhuma nomeie a outra. Tirar o enum de streams foi metade do que desfez o ciclo mutuo
 * session <-> streams; a outra metade foi levar o carregamento de um arquivo gravado para
 * fora da Session.
 *
 * O nome nao ganhou prefixo, ao contrario do que aconteceu com o TerminalFunction: la havia
 * dois enums chamados Function no projeto e ler os dois no mesmo arquivo era ambiguo, e aqui
 * Source e o unico tipo com esse nome. Os nomes das constantes tambem nao mudaram, e nada as
 * persiste por ordinal - o arquivo de replay grava as palavras "Client" e "Server", que sao
 * escolhidas por quem grava e nao pelo enum.
 */
// -----------------------------------------------------------------------------------//
public enum Source
// -----------------------------------------------------------------------------------//
{
  CLIENT, SERVER
}
