package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

// Formato de lista de dataset do DSLIST do ISPF (geometria na tela e leitura de campos).
// -----------------------------------------------------------------------------------//
interface DatasetListLayout
// -----------------------------------------------------------------------------------//
{
  int linesPerDataset ();

  int firstDataRow ();

  // Preenche summary (tabela do assistant) e delta (DatasetStore).
  // Cada formato valida rowFields.size(); se divergir, o delta e salvo apenas com o nome.
  void read (DatasetSummary summary, List<Field> rowFields, Dataset delta);
}
