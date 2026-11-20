package com.bytezone.dm3270.application;

import java.util.IdentityHashMap;
import java.util.Map;

import com.bytezone.dm3270.session.SessionRecord;
import com.bytezone.dm3270.session.SessionRecordListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

// Mantem ObservableList para TableView e indice por identidade (SessionRecord sem equals).
// recordAdded executa na thread do chamador (incl. socket em Spy) por compatibilidade.
// -----------------------------------------------------------------------------------//
class SessionRows implements SessionRecordListener
// -----------------------------------------------------------------------------------//
{
  private final ObservableList<SessionRow> rows = FXCollections.observableArrayList ();
  private final Map<SessionRecord, SessionRow> index = new IdentityHashMap<> ();

  // ---------------------------------------------------------------------------------//
  ObservableList<SessionRow> getRows ()
  // ---------------------------------------------------------------------------------//
  {
    return rows;
  }

  // ---------------------------------------------------------------------------------//
  SessionRow rowFor (SessionRecord sessionRecord)
  // ---------------------------------------------------------------------------------//
  {
    return sessionRecord == null ? null : index.get (sessionRecord);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void recordAdded (SessionRecord sessionRecord)
  // ---------------------------------------------------------------------------------//
  {
    SessionRow row = new SessionRow (sessionRecord);
    index.put (sessionRecord, row);
    rows.add (row);
  }
}
