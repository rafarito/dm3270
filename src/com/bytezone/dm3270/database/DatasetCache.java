package com.bytezone.dm3270.database;

import java.util.Map;
import java.util.TreeMap;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.Member;

/*
 * O cache de datasets e membros que o DatabaseThread mantem enquanto atende a fila.
 *
 * Estava espalhado em treze trechos dentro do DatabaseThread, cada um repetindo o get, a
 * decisao entre criar e reaproveitar, e a escrita. Aqui e a mesma coisa, com nome.
 *
 * REPARE NO QUE NAO EXISTE AQUI: nenhum metodo devolve um Dataset ou um Member. Sao sete
 * operacoes e todas escrevem. Nenhuma requisicao e respondida a partir deste mapa -
 * findDataset e findMember vao ao banco todas as vezes, mesmo quando a entrada esta aqui -, e
 * o unico chamador de addMember descarta o valor de retorno. Reunir as escrituras num lugar so
 * foi o que tornou isso visivel de relance; esta registrado como item 10 do
 * BACKLOG-DEFEITOS.md, e a remocao pertence a onda de limpeza, porque arrasta o CacheEntry e
 * os sete testes dele.
 *
 * Uma armadilha preservada: addMember, putMember e replaceDataset assumem que a entrada
 * existe, e estouram se nao existir. E o comportamento de antes, onde os tres eram cache.get
 * seguido de chamada direta - por uma guarda aqui trocaria a excecao por um descarte
 * silencioso.
 */
// -----------------------------------------------------------------------------------//
final class DatasetCache
// -----------------------------------------------------------------------------------//
{
  private final Map<String, CacheEntry> entries = new TreeMap<> ();

  // ---------------------------------------------------------------------------------//
  void clear ()
  // ---------------------------------------------------------------------------------//
  {
    entries.clear ();
  }

  /*
   * Guarda o dataset, substituindo o que estiver la. O comentario original desta linha era
   * "is this necessary?" - e continua sem resposta.
   */
  // ---------------------------------------------------------------------------------//
  void remember (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    CacheEntry entry = entries.get (dataset.getName ());
    if (entry == null)
      entries.put (dataset.getName (), new CacheEntry (dataset));
    else
      entry.replace (dataset);
  }

  // Guarda so se ainda nao houver entrada: o que ja esta la nao e tocado.
  // ---------------------------------------------------------------------------------//
  void rememberIfAbsent (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    if (entries.get (dataset.getName ()) == null)
      entries.put (dataset.getName (), new CacheEntry (dataset));
  }

  // Cria uma entrada nova por cima de qualquer uma que exista, perdendo os membros dela.
  // ---------------------------------------------------------------------------------//
  void put (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    entries.put (dataset.getName (), new CacheEntry (dataset));
  }

  // ---------------------------------------------------------------------------------//
  void replaceDataset (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    entries.get (dataset.getName ()).dataset = dataset;
  }

  // Funde no membro que ja estiver guardado, se houver.
  // ---------------------------------------------------------------------------------//
  void addMember (Dataset dataset, Member member)
  // ---------------------------------------------------------------------------------//
  {
    entries.get (dataset.getName ()).addMember (member);
  }

  // Substitui sem fundir.
  // ---------------------------------------------------------------------------------//
  void putMember (Dataset dataset, Member member)
  // ---------------------------------------------------------------------------------//
  {
    entries.get (dataset.getName ()).putMember (member);
  }
}
