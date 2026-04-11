package com.bytezone.dm3270.database;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

// -----------------------------------------------------------------------------------//
public class Member
// -----------------------------------------------------------------------------------//
{
  private static final SimpleDateFormat fmt1 = new SimpleDateFormat ("yyyy/MM/dd");
  private static final SimpleDateFormat fmt2 =
      new SimpleDateFormat ("yyyy/MM/dd HH:mm:ss");

  final String name;
  final Dataset dataset;

  String id;
  int size;
  int init;
  int mod;
  int vv;
  int mm;

  Date created;
  Date changed;
  java.sql.Date createdSQL;
  java.sql.Date changedSQL;

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
      System.out.printf ("Invalid created date: [%s]%n", created);
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
      System.out.printf ("Invalid changed date: [%s]%n", changed);
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
  void merge (Member other)
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
  boolean differsFrom (Member other)
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