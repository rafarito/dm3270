package com.bytezone.dm3270.database;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class Member
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (Member.class);

  private static final SimpleDateFormat fmt1 = new SimpleDateFormat ("yyyy/MM/dd");
  private static final SimpleDateFormat fmt2 =
      new SimpleDateFormat ("yyyy/MM/dd HH:mm:ss");

  private final String name;
  private final Dataset dataset;

  private String id;
  private int size;
  private int init;
  private int mod;
  private int vv;
  private int mm;

  private Date created;
  private Date changed;
  private java.sql.Date createdSQL;
  private java.sql.Date changedSQL;

  // ---------------------------------------------------------------------------------//
  public Member (Dataset dataset, String name)
  // ---------------------------------------------------------------------------------//
  {
    this.dataset = dataset;
    this.name = name;
  }

  // ---------------------------------------------------------------------------------//
  public void setID (String id)
  // ---------------------------------------------------------------------------------//
  {
    this.id = id;
  }

  // ---------------------------------------------------------------------------------//
  public void setSize (int size)
  // ---------------------------------------------------------------------------------//
  {
    this.size = size;
  }

  // ---------------------------------------------------------------------------------//
  public void setSize (int size, int init, int mod, int vv, int mm)
  // ---------------------------------------------------------------------------------//
  {
    this.size = size;
    this.init = init;
    this.mod = mod;
    this.vv = vv;
    this.mm = mm;
  }

  // ---------------------------------------------------------------------------------//
  public void setDates (String created, String changed)
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
      if (!changed.trim ().isEmpty ())
      {
        this.changed = fmt2.parse (changed);
        this.changedSQL = new java.sql.Date (this.changed.getTime ());
      }
    }
    catch (ParseException e)
    {
      logger.warn ("Invalid changed date: [{}]", changed, e);
    }
  }

  // ---------------------------------------------------------------------------------//
  public void setDates (java.sql.Date createdSQL, java.sql.Date changedSQL)
  // ---------------------------------------------------------------------------------//
  {
    this.createdSQL = createdSQL;
    if (createdSQL != null)
      created = new Date (createdSQL.getTime ());

    this.changedSQL = changedSQL;
    if (changedSQL != null)
      changed = new Date (changedSQL.getTime ());
  }

  // ---------------------------------------------------------------------------------//
  public void merge (Member other)
  // ---------------------------------------------------------------------------------//
  {
    assert dataset.getName ().equals (other.dataset.getName ());

    if (other.size > 0)
      size = other.size;

    if (other.init > 0)
      init = other.init;

    if (other.mod > 0)
      mod = other.mod;

    if (other.vv > 0)
      vv = other.vv;

    if (other.mm > 0)
      mm = other.mm;

    if (other.id != null)
      id = other.id;

    if (other.created != null)
    {
      created = other.created;
      createdSQL = other.createdSQL;
    }

    if (other.changed != null)
    {
      changed = other.changed;
      changedSQL = other.changedSQL;
    }
  }

  // ---------------------------------------------------------------------------------//
  public boolean differsFrom (Member other)
  // ---------------------------------------------------------------------------------//
  {
    //    System.out.println ("Comparing:");
    //    System.out.println (this);
    //    System.out.println (other);

    if (other.size > 0 && size != other.size)
      return true;

    if (other.init > 0 && init != other.init)
      return true;

    if (other.mod > 0 && mod != other.mod)
      return true;

    if (other.vv > 0 && vv != other.vv)
      return true;

    if (other.mm > 0 && mm != other.mm)
      return true;

    if (other.id != null && !other.id.equals (id))
      return true;

    long createdLong = created == null ? 0 : created.getTime ();
    long createdLong2 = other.created == null ? 0 : other.created.getTime ();
    if (createdLong2 > 0 && createdLong != createdLong2)
      return true;

    long changedLong = changed == null ? 0 : changed.getTime ();
    long changedLong2 = other.changed == null ? 0 : other.changed.getTime ();
    if (changedLong2 > 0 && changedLong != changedLong2)
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
  // Leitura
  // ---------------------------------------------------------------------------------//

  /*
   * Pelo mesmo motivo do Dataset: o DatabaseThread montava o INSERT_MEMBER lendo estes oito
   * campos direto, e o CacheEntry indexava o mapa por member.name. Sao acessores de leitura;
   * nada de fora escreve num Member.
   *
   * getCreatedSQL e getChangedSQL devolvem o campo tal como esta guardado, e nao uma data
   * derivada de created e changed - ver a explicacao equivalente em Dataset.
   */
  // ---------------------------------------------------------------------------------//
  public Dataset getDataset ()
  // ---------------------------------------------------------------------------------//
  {
    return dataset;
  }

  // ---------------------------------------------------------------------------------//
  public String getId ()
  // ---------------------------------------------------------------------------------//
  {
    return id;
  }

  // ---------------------------------------------------------------------------------//
  public int getSize ()
  // ---------------------------------------------------------------------------------//
  {
    return size;
  }

  // ---------------------------------------------------------------------------------//
  public int getInit ()
  // ---------------------------------------------------------------------------------//
  {
    return init;
  }

  // ---------------------------------------------------------------------------------//
  public int getMod ()
  // ---------------------------------------------------------------------------------//
  {
    return mod;
  }

  // ---------------------------------------------------------------------------------//
  public int getVv ()
  // ---------------------------------------------------------------------------------//
  {
    return vv;
  }

  // ---------------------------------------------------------------------------------//
  public int getMm ()
  // ---------------------------------------------------------------------------------//
  {
    return mm;
  }

  // ---------------------------------------------------------------------------------//
  public java.sql.Date getCreatedSQL ()
  // ---------------------------------------------------------------------------------//
  {
    return createdSQL;
  }

  // ---------------------------------------------------------------------------------//
  public java.sql.Date getChangedSQL ()
  // ---------------------------------------------------------------------------------//
  {
    return changedSQL;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    String createdText = created == null ? "" : fmt1.format (created);
    String changedText = changed == null ? "" : fmt2.format (changed);
    String idText = id == null ? "" : id;

    return String.format ("%-8s  %3d  %-8s %4d  %2d  %2d  %2d %s %s", name, size, idText,
        init, mod, vv, mm, createdText, changedText);
  }
}