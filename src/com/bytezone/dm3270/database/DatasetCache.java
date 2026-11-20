package com.bytezone.dm3270.database;

import java.util.Map;
import java.util.TreeMap;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.Member;

// Cache atualmente write-only (nenhuma consulta le dele).
// replaceDataset, addMember e putMember assumem entrada existente (NPE se ausente).
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

  // ---------------------------------------------------------------------------------//
  void rememberIfAbsent (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    if (entries.get (dataset.getName ()) == null)
      entries.put (dataset.getName (), new CacheEntry (dataset));
  }

  // Sobrescreve a entrada existente, descartando seus membros.
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
