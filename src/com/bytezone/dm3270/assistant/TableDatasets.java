package com.bytezone.dm3270.assistant;

import java.util.IdentityHashMap;
import java.util.Map;

import com.bytezone.dm3270.datasets.DatasetSummary;

/*
 * A ponte entre o que o ScreenWatcher observou e a linha que a TableView desenha.
 *
 * O ScreenWatcher produzia TableDataset direto, e por isso o pacote display nomeava
 * assistant. Agora produz DatasetSummary, que e o mesmo dado sem JavaFX, e a conversao para o
 * modelo de linha acontece aqui - na borda da interface, o unico lugar que precisa de
 * StringProperty.
 *
 * A IDENTIDADE DA LINHA E CARGA UTIL, e e o ponto delicado desta classe.
 * DatasetTable.addDataset e DatasetTreeTable.addDataset guardam a PRIMEIRA instancia que
 * recebem com um dado nome e ignoram as seguintes - a fusao esta comentada nos dois. O que
 * fazia a tabela mostrar dados que so aparecem numa tela posterior era o ScreenWatcher mutar
 * aquele mesmo objeto: as properties do JavaFX propagavam a mudanca para a TableView
 * sozinhas. Devolver um TableDataset novo a cada tela congelaria a tabela nos dados da
 * primeira aparicao, e nenhum teste automatico pegaria, porque e comportamento de interface.
 *
 * POR QUE O MAPA E POR IDENTIDADE DO SUMMARY, E NAO POR NOME. Porque o tempo de vida tem de
 * ser o mesmo do acumulador do ScreenWatcher, e ele nao e eterno: FieldManager.setScreenDimensions
 * CONSTROI UM ScreenWatcher NOVO, com o mapa de datasets vazio. Depois disso, o mesmo nome
 * volta como um DatasetSummary novo, contendo so o que a tela atual mostrou.
 *
 * Com um mapa por nome, esse summary recem-nascido seria copiado por cima da linha antiga e
 * apagaria os campos acumulados antes. Com o mapa por identidade, ele ganha uma linha nova - e
 * a linha antiga, que a tabela ja guardou, fica intocada. E exatamente o que acontecia quando
 * o ScreenWatcher produzia TableDataset: apos uma troca de dimensoes a tabela conserva o que
 * tinha e para de acompanhar aquele dataset. Nao e um comportamento bonito, mas e o que existe,
 * e a onda 3 nao muda comportamento.
 */
// -----------------------------------------------------------------------------------//
public class TableDatasets
// -----------------------------------------------------------------------------------//
{
  private final Map<DatasetSummary, TableDataset> rows = new IdentityHashMap<> ();

  // ---------------------------------------------------------------------------------//
  public TableDataset rowFor (DatasetSummary summary)
  // ---------------------------------------------------------------------------------//
  {
    TableDataset row = rows.get (summary);

    if (row == null)
    {
      row = new TableDataset (summary.getDatasetName ());
      rows.put (summary, row);
    }

    copy (summary, row);

    return row;
  }

  /*
   * O nome nao entra na copia: foi dado no construtor e nunca muda depois - o ScreenWatcher
   * indexa o proprio acumulador por ele.
   */
  // ---------------------------------------------------------------------------------//
  private static void copy (DatasetSummary summary, TableDataset row)
  // ---------------------------------------------------------------------------------//
  {
    row.setVolume (summary.getVolume ());
    row.setDevice (summary.getDevice ());
    row.setCatalog (summary.getCatalog ());
    row.setCreated (summary.getCreated ());
    row.setExpires (summary.getExpires ());
    row.setReferredDate (summary.getReferredDate ());
    row.setReferredTime (summary.getReferredTime ());
    row.setDsorg (summary.getDsorg ());
    row.setRecfm (summary.getRecfm ());
    row.setTracks (summary.getTracks ());
    row.setCylinders (summary.getCylinders ());
    row.setExtents (summary.getExtents ());
    row.setPercentUsed (summary.getPercentUsed ());
    row.setLrecl (summary.getLrecl ());
    row.setBlksize (summary.getBlksize ());
  }
}
