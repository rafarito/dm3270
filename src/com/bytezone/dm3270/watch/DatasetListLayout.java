package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.screen.Field;

/*
 * Um dos formatos de lista de dataset que o DSLIST do ISPF produz.
 *
 * Antes, cada formato existia como um numero - o screenType, de 1 a 5 - repetido em DOIS
 * switch paralelos: um em checkDatasetList, que classificava a tela pela quantidade de campos
 * da linha de titulos, e outro em addDataset, que repetia a mesma numeracao e lia os dados por
 * deslocamento de coluna cravado no codigo. Eram quatro conjuntos de numeros em dois lugares:
 * suportar um formato novo obrigava a editar os dois, e nada garantia que ficassem coerentes.
 *
 * Agora cada formato e uma classe que carrega os PROPRIOS deslocamentos. Os numeros sao os
 * mesmos, um por um; o que mudou foi onde eles moram.
 *
 * As duas medidas de geometria fazem parte do formato tanto quanto as colunas: um dataset
 * ocupa uma, duas ou tres linhas da tela, e por isso a lista comeca numa linha diferente em
 * cada caso.
 */
// -----------------------------------------------------------------------------------//
interface DatasetListLayout
// -----------------------------------------------------------------------------------//
{
  // Quantas linhas da tela um dataset ocupa.
  int linesPerDataset ();

  // A primeira linha de dados, ja depois dos titulos.
  int firstDataRow ();

  /*
   * Le uma entrada da lista.
   *
   * O summary e o que a tabela do assistant mostra; o delta e o que vai para o DatasetStore.
   * Os dois sao preenchidos aqui, e nem sempre com as mesmas coisas - qual campo vai para qual
   * dos dois faz parte do formato, e ha um caso em que a diferenca e um defeito conhecido
   * (item 9 do BACKLOG-DEFEITOS.md).
   *
   * As guardas sobre rowFields.size () tambem sao parte do formato: uma linha com uma
   * quantidade de campos diferente da esperada nao e lida, e o delta segue para a persistencia
   * so com o nome, porque quem chama grava de qualquer jeito.
   */
  void read (DatasetSummary summary, List<Field> rowFields, Dataset delta);
}
