package com.bytezone.dm3270.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetSummary;

/*
 * A leitura dos blocos de detalhe de um dataset a partir de um trecho de texto da tela.
 *
 * Sao os tres blocos que aparecem em mais de um layout - espaco, disposicao e datas -, cada um
 * recebendo de quem o chama os deslocamentos de coluna do seu formato. O corte de cada bloco
 * continua guardado pela mesma cadeia de comparacoes de comprimento do original: um texto
 * curto demais preenche so os primeiros campos, e um texto vazio nao preenche nenhum.
 *
 * O logger e o do ScreenWatcher, de proposito e nao por descuido. O nome do logger faz parte
 * da linha de log, e estas mensagens sairam de la - declarar um logger proprio mudaria saida
 * observavel, o que a Regra 1 desta refatoracao nao permite.
 */
// -----------------------------------------------------------------------------------//
final class DatasetDetails
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (ScreenWatcher.class);

  // ---------------------------------------------------------------------------------//
  private DatasetDetails ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  static void setSpace (DatasetSummary dataset, String details, int t1, int t2, int t3)
  // ---------------------------------------------------------------------------------//
  {
    if (details.trim ().isEmpty ())
      return;

    if (details.length () >= t1)
      dataset.setTracks (getInteger ("tracks", details.substring (0, t1).trim ()));
    if (details.length () >= t2)
      dataset.setPercentUsed (getInteger ("pct", details.substring (t1, t2).trim ()));
    if (details.length () >= t3)
      dataset.setExtents (getInteger ("ext", details.substring (t2, t3).trim ()));
    if (details.length () > t3)
      dataset.setDevice (details.substring (t3).trim ());
  }

  // ---------------------------------------------------------------------------------//
  static void setDisposition (DatasetSummary dataset, String details, int t1, int t2, int t3)
  // ---------------------------------------------------------------------------------//
  {
    if (details.trim ().isEmpty ())
      return;

    if (details.length () >= t1)
      dataset.setDsorg (details.substring (0, t1).trim ());
    if (details.length () >= t2)
      dataset.setRecfm (details.substring (t1, t2).trim ());
    if (details.length () >= t3)
      dataset.setLrecl (getInteger ("lrecl", details.substring (t2, t3).trim ()));
    if (details.length () > t3)
      dataset.setBlksize (getInteger ("blksize", details.substring (t3).trim ()));
  }

  /*
   * As datas saem em dois blocos de 11 e o resto, e os deslocamentos aqui sao fixos - nenhum
   * layout os varia. O summary guarda o texto observado e o delta guarda a data convertida: e
   * a razao de os dois tipos existirem, e uma data que o host escreva fora do formato fica
   * nula no delta e visivel no summary.
   */
  // ---------------------------------------------------------------------------------//
  static void setDates (DatasetSummary dataset, String details, Dataset ds)
  // ---------------------------------------------------------------------------------//
  {
    if (details.trim ().isEmpty ())
      return;

    String created = details.substring (0, 11).trim ();
    String expires = details.substring (11, 22).trim ();
    String referred = details.substring (22).trim ();

    dataset.setCreated (created);
    dataset.setExpires (expires);
    dataset.setReferredDate (referred);

    ds.setDates (created, expires, referred);
  }

  // ---------------------------------------------------------------------------------//
  static int getInteger (String id, String value)
  // ---------------------------------------------------------------------------------//
  {
    if (value == null || value.isEmpty () || value.equals ("?"))
      return 0;

    try
    {
      return Integer.parseInt (value);
    }
    catch (NumberFormatException e)
    {
      logger.error ("ParseInt error with {}: [{}]", id, value, e);
      return 0;
    }
  }
}
