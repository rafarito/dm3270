package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

/*
 * O que os dois layouts de varias linhas por dataset tem em comum.
 *
 * Eram os screenType 4 e 5, e a linha de titulos dos dois e a mesma - seis campos, "Message" e
 * "Volume" -, o que obriga o reconhecimento a olhar a linha seguinte para distinguir um do
 * outro. Dali em diante eles divergem: quantas linhas cada dataset ocupa, se ha coluna de
 * catalogo, e o que chega a persistencia.
 *
 * Os deslocamentos ficam aqui, num lugar so, e nao repetidos nas duas subclasses. Duplica-los
 * seria recriar em ponto pequeno exatamente a duplicacao que este trabalho desfez - e sao
 * deslocamentos diferentes dos layouts de uma linha, que e o que torna facil errar.
 */
// -----------------------------------------------------------------------------------//
abstract class MultiLineDatasetLayout implements DatasetListLayout
// -----------------------------------------------------------------------------------//
{
  private static final int TRACKS = 6;
  private static final int PERCENT_USED = 10;
  private static final int EXTENTS = 14;

  private static final int DSORG = 5;
  private static final int RECFM = 10;
  private static final int LRECL = 16;

  /*
   * Espaco, disposicao e datas: os tres blocos que os dois leem das mesmas colunas e escrevem
   * nos mesmos lugares. O que cada subclasse faz antes e depois disto e diferente, e e por
   * isso que elas continuam sendo duas.
   */
  // ---------------------------------------------------------------------------------//
  final void readDetails (DatasetSummary summary, List<Field> rowFields, Dataset delta)
  // ---------------------------------------------------------------------------------//
  {
    DatasetDetails.setSpace (summary, rowFields.get (3).getText (), TRACKS, PERCENT_USED,
        EXTENTS);
    DatasetDetails.setDisposition (summary, rowFields.get (4).getText (), DSORG, RECFM, LRECL);
    DatasetDetails.setDates (summary, rowFields.get (5).getText (), delta);

    delta.setSpace (summary.getTracks (), summary.getCylinders (), summary.getExtents (),
        summary.getPercentUsed ());
    delta.setDisposition (summary.getDsorg (), summary.getRecfm (), summary.getLrecl (),
        summary.getBlksize ());
  }
}
