package com.bytezone.dm3270.session;

/*
 * Avisa que um registro entrou na sessao.
 *
 * A Session guardava um ObservableList do JavaFX e o entregava inteiro, por getDataRecords (),
 * para que a tabela de replay o observasse. A lista observavel era a unica coisa que ligava o
 * acumulado da conversa ao widget - e arrastava javafx.collections para dentro de um tipo que
 * nao tem nada de grafico.
 *
 * A porta esta declarada aqui, no lado que CONSOME a abstracao: a Session precisa de um lugar
 * onde avisar, nao de uma lista que sabe se desenhar. Quem implementa e a projecao na borda da
 * interface, application.SessionRows, que mantem a ObservableList de linhas.
 *
 * O AVISO SAI NA MESMA HORA EM QUE A LISTA OBSERVAVEL DISPARAVA, e isso importa: a
 * ObservableList notificava dentro de add (), ANTES do bloco que identifica cliente e servidor.
 * A linha aparecia na tabela e so depois o cabecalho mudava. A ordem foi preservada.
 *
 * E CONTINUA SAINDO DA THREAD DO SOCKET no modo Spy, sem Platform.runLater, exatamente como
 * antes. Isso e um defeito conhecido - esta no backlog -, e reproduzi-lo e o que a Regra 1
 * exige deste passo.
 */
// -----------------------------------------------------------------------------------//
public interface SessionRecordListener
// -----------------------------------------------------------------------------------//
{
  void recordAdded (SessionRecord sessionRecord);
}
