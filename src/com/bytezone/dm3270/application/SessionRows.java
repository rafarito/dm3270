package com.bytezone.dm3270.application;

import java.util.IdentityHashMap;
import java.util.Map;

import com.bytezone.dm3270.session.SessionRecord;
import com.bytezone.dm3270.session.SessionRecordListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/*
 * A ponte entre os registros que a Session acumulou e as linhas que a TableView desenha.
 *
 * A Session guardava ela mesma um ObservableList<SessionRecord> e o entregava inteiro para o
 * SpyPane e o ReplayStage. Agora guarda uma List comum e avisa por SessionRecordListener; a
 * lista observavel vive aqui, na borda da interface, que e o unico lugar que precisa dela.
 *
 * O padrao e o de assistant.TableDatasets, e o mapa por identidade tambem - mas por um motivo
 * diferente, que vale registrar. La a identidade e necessaria porque o ScreenWatcher MUTA o
 * mesmo objeto entre telas. Aqui um SessionRecord nunca muda: o mapa existe porque o
 * ReplayStage.displayFirstScreen escolhe a primeira tela util pelo TAMANHO do registro,
 * chamando session.getBySize (), e precisa transformar aquele registro na linha que a tabela
 * ja tem para poder seleciona-la. Sem o mapa nao ha como ir do dominio de volta para a linha.
 *
 * Por identidade, e nao por equals, porque SessionRecord nao define equals - dois registros
 * com o mesmo conteudo sao linhas diferentes, e e assim que uma sessao com mensagens repetidas
 * aparece hoje.
 *
 * AS LINHAS SAO ACRESCENTADAS NA THREAD DE QUEM AVISOU. No modo Spy isso e a thread do socket,
 * sem Platform.runLater, mutando uma ObservableList que a tabela esta observando - exatamente
 * como a Session fazia antes. E um defeito conhecido, esta no backlog, e reproduzi-lo e o que
 * a Regra 1 exige: envolver em runLater aqui mudaria o instante em que cada linha aparece.
 */
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
