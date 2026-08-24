package com.bytezone.dm3270.database;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class Dataset
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (Dataset.class);

  private static final SimpleDateFormat fmt1 = new SimpleDateFormat ("yyyy/MM/dd");

  private final String name;

  private String volume;
  private String device;
  private String catalog;

  private int tracks;
  private int cylinders;
  private int extents;
  private int percent;

  private String dsorg;
  private String recfm;
  private int lrecl;
  private int blksize;

  private Date created;
  private Date expires;
  private Date referred;

  private java.sql.Date createdSQL;
  private java.sql.Date expiresSQL;
  private java.sql.Date referredSQL;

  // ---------------------------------------------------------------------------------//
  public Dataset (String name)
  // ---------------------------------------------------------------------------------//
  {
    this.name = name;
  }

  // ---------------------------------------------------------------------------------//
  public void setLocation (String volume, String device, String catalog)
  // ---------------------------------------------------------------------------------//
  {
    this.volume = volume;
    this.device = device;
    this.catalog = catalog;
  }

  // ---------------------------------------------------------------------------------//
  public void setVolume (String volume)
  // ---------------------------------------------------------------------------------//
  {
    this.volume = volume;
  }

  // ---------------------------------------------------------------------------------//
  public void setCatalog (String catalog)
  // ---------------------------------------------------------------------------------//
  {
    this.catalog = catalog;
  }

  // ---------------------------------------------------------------------------------//
  public void setDevice (String device)
  // ---------------------------------------------------------------------------------//
  {
    this.device = device;
  }

  // ---------------------------------------------------------------------------------//
  public void setSpace (int tracks, int cylinders, int extents, int percent)
  // ---------------------------------------------------------------------------------//
  {
    this.tracks = tracks;
    this.cylinders = cylinders;
    this.extents = extents;
    this.percent = percent;
  }

  // ---------------------------------------------------------------------------------//
  public void setDisposition (String dsorg, String recfm, int lrecl, int blksize)
  // ---------------------------------------------------------------------------------//
  {
    this.dsorg = dsorg;
    this.recfm = recfm;
    this.lrecl = lrecl;
    this.blksize = blksize;
  }

  // ---------------------------------------------------------------------------------//
  public void setDates (String created, String expires, String referred)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      if (!created.trim ().isEmpty ())
      {
        this.created = fmt1.parse (created);
        this.createdSQL = new java.sql.Date (this.created.getTime ());
      }
    }
    catch (ParseException e)
    {
      logger.warn ("Invalid created date: [{}]", created, e);
    }

    try
    {
      if (!expires.trim ().isEmpty ())
      {
        this.expires = fmt1.parse (expires);
        this.expiresSQL = new java.sql.Date (this.expires.getTime ());
      }
    }
    catch (ParseException e)
    {
      logger.warn ("Invalid expires date: [{}]", expires, e);
    }

    try
    {
      if (!referred.trim ().isEmpty ())
      {
        this.referred = fmt1.parse (referred);
        this.referredSQL = new java.sql.Date (this.referred.getTime ());
      }
    }
    catch (ParseException e)
    {
      logger.warn ("Invalid referred date: [{}]", referred, e);
    }
  }

  // ---------------------------------------------------------------------------------//
  public void setDates (java.sql.Date createdSQL, java.sql.Date expiresSQL,
      java.sql.Date referredSQL)
  // ---------------------------------------------------------------------------------//
  {
    this.createdSQL = createdSQL;
    this.expiresSQL = expiresSQL;
    this.referredSQL = referredSQL;

    if (createdSQL != null)
      created = new Date (createdSQL.getTime ());
    if (expiresSQL != null)
      expires = new Date (expiresSQL.getTime ());
    if (referredSQL != null)
      referred = new Date (referredSQL.getTime ());
  }

  // ---------------------------------------------------------------------------------//
  public void merge (Dataset other)
  // ---------------------------------------------------------------------------------//
  {
    assert name.equals (other.name);

    if (other.tracks > 0)
      tracks = other.tracks;
    if (other.cylinders > 0)
      cylinders = other.cylinders;
    if (other.extents > 0)
      extents = other.extents;
    if (other.percent > 0)
      percent = other.percent;

    if (other.dsorg != null)
      dsorg = other.dsorg;
    if (other.recfm != null)
      recfm = other.recfm;
    if (other.lrecl > 0)
      lrecl = other.lrecl;
    if (other.blksize > 0)
      blksize = other.blksize;

    if (other.volume != null)
      volume = other.volume;
    if (other.device != null)
      device = other.device;
    if (other.catalog != null)
      catalog = other.catalog;

    if (other.created != null)
    {
      created = other.created;
      createdSQL = other.createdSQL;
    }

    if (other.referred != null)
    {
      referred = other.referred;
      referredSQL = other.referredSQL;
    }

    if (other.expires != null)
    {
      expires = other.expires;
      expiresSQL = other.expiresSQL;
    }
  }

  // ---------------------------------------------------------------------------------//
  public boolean differsFrom (Dataset other)
  // ---------------------------------------------------------------------------------//
  {
    //    System.out.println ("Comparing:");
    //    System.out.println (this);
    //    System.out.println (other);

    if (other.tracks > 0 && tracks != other.tracks)
      return true;
    if (other.cylinders > 0 && cylinders != other.cylinders)
      return true;
    if (other.extents > 0 && extents != other.extents)
      return true;
    if (other.percent > 0 && percent != other.percent)
      return true;

    if (other.dsorg != null && !other.dsorg.equals (dsorg))
      return true;
    if (other.recfm != null && !other.recfm.equals (recfm))
      return true;
    if (other.lrecl > 0 && lrecl != other.lrecl)
      return true;
    if (other.blksize > 0 && blksize != other.blksize)
      return true;

    if (other.volume != null && !other.volume.equals (volume))
      return true;
    if (other.device != null && !other.device.equals (device))
      return true;
    if (other.catalog != null && !other.catalog.equals (catalog))
      return true;

    long createdLong = created == null ? 0 : created.getTime ();
    long createdLong2 = other.created == null ? 0 : other.created.getTime ();
    if (createdLong2 > 0 && createdLong != createdLong2)
      return true;

    long referredLong = referred == null ? 0 : referred.getTime ();
    long referredLong2 = other.referred == null ? 0 : other.referred.getTime ();
    if (referredLong2 > 0 && referredLong != referredLong2)
      return true;

    long expiresLong = expires == null ? 0 : expires.getTime ();
    long expiresLong2 = other.expires == null ? 0 : other.expires.getTime ();
    if (expiresLong2 > 0 && expiresLong != expiresLong2)
      return true;

    return false;
  }

  // ---------------------------------------------------------------------------------//
  public String getName ()
  // ---------------------------------------------------------------------------------//
  {
    return name;
  }

  // ---------------------------------------------------------------------------------//
  public boolean isPartitioned ()
  // ---------------------------------------------------------------------------------//
  {
    return dsorg != null && dsorg.equals ("PO");
  }

  // ---------------------------------------------------------------------------------//
  // Leitura
  // ---------------------------------------------------------------------------------//

  /*
   * O DatabaseThread montava o SQL lendo estes campos direto, sem passar por metodo
   * nenhum - quinze acessos de fora para dentro, num tipo cujo unico proposito e carregar
   * o que a tela mostrou ate a persistencia. Enquanto isso valesse, Dataset so podia morar
   * no mesmo pacote que a thread do banco, e o pacote display - que so usa os setters -
   * ficava preso a persistencia junto.
   *
   * Sao acessores de LEITURA: a unica escrita que vinha de fora era dsorg = "PO", que virou
   * markPartitioned () logo abaixo.
   *
   * Sobre getCreatedSQL, getExpiresSQL e getReferredSQL devolverem java.sql.Date: e o
   * campo tal como esta guardado, de proposito. Derivar a data SQL de created na hora de
   * gravar pareceria equivalente e nao e - setDates (java.sql.Date...) atribui createdSQL
   * mesmo quando o argumento e null, mas so atribui created quando nao e, entao os dois
   * podem divergir. Reproduzir o campo e o que preserva o comportamento.
   */
  // ---------------------------------------------------------------------------------//
  public String getVolume ()
  // ---------------------------------------------------------------------------------//
  {
    return volume;
  }

  // ---------------------------------------------------------------------------------//
  public String getDevice ()
  // ---------------------------------------------------------------------------------//
  {
    return device;
  }

  // ---------------------------------------------------------------------------------//
  public String getCatalog ()
  // ---------------------------------------------------------------------------------//
  {
    return catalog;
  }

  // ---------------------------------------------------------------------------------//
  public int getTracks ()
  // ---------------------------------------------------------------------------------//
  {
    return tracks;
  }

  // ---------------------------------------------------------------------------------//
  public int getCylinders ()
  // ---------------------------------------------------------------------------------//
  {
    return cylinders;
  }

  // ---------------------------------------------------------------------------------//
  public int getExtents ()
  // ---------------------------------------------------------------------------------//
  {
    return extents;
  }

  // ---------------------------------------------------------------------------------//
  public int getPercent ()
  // ---------------------------------------------------------------------------------//
  {
    return percent;
  }

  // ---------------------------------------------------------------------------------//
  public String getDsorg ()
  // ---------------------------------------------------------------------------------//
  {
    return dsorg;
  }

  // ---------------------------------------------------------------------------------//
  public String getRecfm ()
  // ---------------------------------------------------------------------------------//
  {
    return recfm;
  }

  // ---------------------------------------------------------------------------------//
  public int getLrecl ()
  // ---------------------------------------------------------------------------------//
  {
    return lrecl;
  }

  // ---------------------------------------------------------------------------------//
  public int getBlksize ()
  // ---------------------------------------------------------------------------------//
  {
    return blksize;
  }

  // ---------------------------------------------------------------------------------//
  public java.sql.Date getCreatedSQL ()
  // ---------------------------------------------------------------------------------//
  {
    return createdSQL;
  }

  // ---------------------------------------------------------------------------------//
  public java.sql.Date getExpiresSQL ()
  // ---------------------------------------------------------------------------------//
  {
    return expiresSQL;
  }

  // ---------------------------------------------------------------------------------//
  public java.sql.Date getReferredSQL ()
  // ---------------------------------------------------------------------------------//
  {
    return referredSQL;
  }

  /*
   * Um dataset que tem membros e particionado por definicao. O DatabaseThread descobre isso
   * ao gravar o primeiro membro, e ate agora escrevia dsorg = "PO" de fora.
   */
  // ---------------------------------------------------------------------------------//
  public void markPartitioned ()
  // ---------------------------------------------------------------------------------//
  {
    dsorg = "PO";
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    String createdText = created == null ? "" : fmt1.format (created);
    String referredText = referred == null ? "" : fmt1.format (referred);
    String expiresText = expires == null ? "" : fmt1.format (expires);

    String dsorgText = dsorg == null ? "" : dsorg;
    String deviceText = device == null ? "" : device;
    String volumeText = volume == null ? "" : volume;
    String recfmText = recfm == null ? "" : recfm;
    String catalogText = catalog == null ? "" : catalog;

    return String.format (
        "%-3s %-31s  %3d %3d  %-6s  %-6s  %3d  %3d  %-4s %4d %6d  %s %s %s %s", dsorgText,
        name, tracks, cylinders, deviceText, volumeText, extents, percent, recfmText,
        lrecl, blksize, catalogText, createdText, referredText, expiresText);
  }
}