package com.bytezone.dm3270.datasets;

/*
 * Um dataset - ou um membro de PDS - como o ScreenWatcher o leu da tela do ISPF.
 *
 * Este tipo e o gemeo sem JavaFX do assistant.TableDataset, e existe pelo motivo de sempre:
 * o TableDataset guarda os mesmos dezesseis campos, mas em StringProperty e IntegerProperty,
 * porque e o modelo de linha de uma TableView. Enquanto o ScreenWatcher produzisse
 * TableDataset, o pacote display nomeava assistant, e os dialogos de transferencia nomeavam
 * assistant tambem - so para ler duas datas. Era a causa unica do ciclo
 * assistant <-> filetransfer.
 *
 * Os campos sao TEXTO OBSERVADO, e nao valores convertidos: o que a tela mostrou, como
 * mostrou. E deliberado, e diferente do datasets.Dataset, que guarda datas ja convertidas
 * para gravar no banco. Uma data que o host escreva fora do formato esperado nao converte, e
 * o Dataset fica com o campo nulo - mas a tabela do assistant continua mostrando o texto. Os
 * dois tipos existem porque os dois usos existem.
 *
 * A ordem dos campos, os nomes dos acessores e o formato do toString sao os do TableDataset,
 * de proposito: o ScreenWatcher chama os mesmos metodos de antes, e quem compara os dois
 * lado a lado ve que sao a mesma coisa.
 */
// -----------------------------------------------------------------------------------//
public class DatasetSummary
// -----------------------------------------------------------------------------------//
{
  private String datasetName;
  private String volume;
  private String device;
  private String catalog;
  private String created;
  private String expires;
  private String referredDate;
  private String referredTime;
  private String dsorg;
  private String recfm;

  private int tracks;
  private int cylinders;
  private int extents;
  private int percentUsed;
  private int lrecl;
  private int blksize;

  // ---------------------------------------------------------------------------------//
  public DatasetSummary (String name)
  // ---------------------------------------------------------------------------------//
  {
    this.datasetName = name;
  }

  // ---------------------------------------------------------------------------------//
  public String getDatasetName ()
  // ---------------------------------------------------------------------------------//
  {
    return datasetName;
  }

  // ---------------------------------------------------------------------------------//
  public void setDatasetName (String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    this.datasetName = datasetName;
  }

  // ---------------------------------------------------------------------------------//
  public String getVolume ()
  // ---------------------------------------------------------------------------------//
  {
    return volume;
  }

  // ---------------------------------------------------------------------------------//
  public void setVolume (String volume)
  // ---------------------------------------------------------------------------------//
  {
    this.volume = volume;
  }

  // ---------------------------------------------------------------------------------//
  public String getDevice ()
  // ---------------------------------------------------------------------------------//
  {
    return device;
  }

  // ---------------------------------------------------------------------------------//
  public void setDevice (String device)
  // ---------------------------------------------------------------------------------//
  {
    this.device = device;
  }

  // ---------------------------------------------------------------------------------//
  public String getCatalog ()
  // ---------------------------------------------------------------------------------//
  {
    return catalog;
  }

  // ---------------------------------------------------------------------------------//
  public void setCatalog (String catalog)
  // ---------------------------------------------------------------------------------//
  {
    this.catalog = catalog;
  }

  // ---------------------------------------------------------------------------------//
  public String getCreated ()
  // ---------------------------------------------------------------------------------//
  {
    return created;
  }

  // ---------------------------------------------------------------------------------//
  public void setCreated (String created)
  // ---------------------------------------------------------------------------------//
  {
    this.created = created;
  }

  // ---------------------------------------------------------------------------------//
  public String getExpires ()
  // ---------------------------------------------------------------------------------//
  {
    return expires;
  }

  // ---------------------------------------------------------------------------------//
  public void setExpires (String expires)
  // ---------------------------------------------------------------------------------//
  {
    this.expires = expires;
  }

  // ---------------------------------------------------------------------------------//
  public String getReferredDate ()
  // ---------------------------------------------------------------------------------//
  {
    return referredDate;
  }

  // ---------------------------------------------------------------------------------//
  public void setReferredDate (String referredDate)
  // ---------------------------------------------------------------------------------//
  {
    this.referredDate = referredDate;
  }

  // ---------------------------------------------------------------------------------//
  public String getReferredTime ()
  // ---------------------------------------------------------------------------------//
  {
    return referredTime;
  }

  // ---------------------------------------------------------------------------------//
  public void setReferredTime (String referredTime)
  // ---------------------------------------------------------------------------------//
  {
    this.referredTime = referredTime;
  }

  // ---------------------------------------------------------------------------------//
  public String getDsorg ()
  // ---------------------------------------------------------------------------------//
  {
    return dsorg;
  }

  // ---------------------------------------------------------------------------------//
  public void setDsorg (String dsorg)
  // ---------------------------------------------------------------------------------//
  {
    this.dsorg = dsorg;
  }

  // ---------------------------------------------------------------------------------//
  public String getRecfm ()
  // ---------------------------------------------------------------------------------//
  {
    return recfm;
  }

  // ---------------------------------------------------------------------------------//
  public void setRecfm (String recfm)
  // ---------------------------------------------------------------------------------//
  {
    this.recfm = recfm;
  }

  // ---------------------------------------------------------------------------------//
  public int getTracks ()
  // ---------------------------------------------------------------------------------//
  {
    return tracks;
  }

  // ---------------------------------------------------------------------------------//
  public void setTracks (int tracks)
  // ---------------------------------------------------------------------------------//
  {
    this.tracks = tracks;
  }

  // ---------------------------------------------------------------------------------//
  public int getCylinders ()
  // ---------------------------------------------------------------------------------//
  {
    return cylinders;
  }

  // ---------------------------------------------------------------------------------//
  public void setCylinders (int cylinders)
  // ---------------------------------------------------------------------------------//
  {
    this.cylinders = cylinders;
  }

  // ---------------------------------------------------------------------------------//
  public int getExtents ()
  // ---------------------------------------------------------------------------------//
  {
    return extents;
  }

  // ---------------------------------------------------------------------------------//
  public void setExtents (int extents)
  // ---------------------------------------------------------------------------------//
  {
    this.extents = extents;
  }

  // ---------------------------------------------------------------------------------//
  public int getPercentUsed ()
  // ---------------------------------------------------------------------------------//
  {
    return percentUsed;
  }

  // ---------------------------------------------------------------------------------//
  public void setPercentUsed (int percentUsed)
  // ---------------------------------------------------------------------------------//
  {
    this.percentUsed = percentUsed;
  }

  // ---------------------------------------------------------------------------------//
  public int getLrecl ()
  // ---------------------------------------------------------------------------------//
  {
    return lrecl;
  }

  // ---------------------------------------------------------------------------------//
  public void setLrecl (int lrecl)
  // ---------------------------------------------------------------------------------//
  {
    this.lrecl = lrecl;
  }

  // ---------------------------------------------------------------------------------//
  public int getBlksize ()
  // ---------------------------------------------------------------------------------//
  {
    return blksize;
  }

  // ---------------------------------------------------------------------------------//
  public void setBlksize (int blksize)
  // ---------------------------------------------------------------------------------//
  {
    this.blksize = blksize;
  }
  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();

    text.append (String.format ("Name ............ %s%n", getDatasetName ()));

    text.append (String.format ("Volume .......... %s%n", getVolume ()));
    text.append (String.format ("Device .......... %s%n", getDevice ()));
    text.append (String.format ("DSORG ........... %s%n", getDsorg ()));
    text.append (String.format ("RECFM ........... %s%n", getRecfm ()));
    text.append (String.format ("Catalog ......... %s%n", getCatalog ()));
    text.append (String.format ("Created ......... %s%n", getCreated ()));
    text.append (String.format ("Expires ......... %s%n", getExpires ()));
    text.append (String.format ("Referred ........ %s%n", getReferredDate ()));

    text.append (String.format ("Tracks .......... %s%n", getTracks ()));
    text.append (String.format ("Cylinders ....... %s%n", getCylinders ()));
    text.append (String.format ("Extents ......... %s%n", getExtents ()));
    text.append (String.format ("Percent used .... %s%n", getPercentUsed ()));
    text.append (String.format ("LRECL ........... %s%n", getLrecl ()));
    text.append (String.format ("BLKSIZE ......... %s  ", getBlksize ()));

    return text.toString ();
  }
}
