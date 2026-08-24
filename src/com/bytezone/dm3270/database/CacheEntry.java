package com.bytezone.dm3270.database;

import java.util.Map;
import java.util.TreeMap;

// -----------------------------------------------------------------------------------//
class CacheEntry
// -----------------------------------------------------------------------------------//
{
  Dataset dataset;
  Map<String, Member> members;

  // ---------------------------------------------------------------------------------//
  public CacheEntry (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    this.dataset = dataset;
  }

  // ---------------------------------------------------------------------------------//
  Member addMember (Member member)
  // ---------------------------------------------------------------------------------//
  {
    if (members == null)
    {
      members = new TreeMap<String, Member> ();
      members.put (member.getName (), member);
      return member;
    }

    Member currentMember = members.get (member.getName ());
    if (currentMember == null)
    {
      members.put (member.getName (), member);
      return member;
    }

    currentMember.merge (member);
    return currentMember;
  }

  // ---------------------------------------------------------------------------------//
  void putMember (Member member)
  // ---------------------------------------------------------------------------------//
  {
    if (members == null)
      members = new TreeMap<String, Member> ();

    members.put (member.getName (), member);
  }

  // ---------------------------------------------------------------------------------//
  void replace (Dataset dataset)
  // ---------------------------------------------------------------------------------//
  {
    assert this.dataset.getName ().equals (dataset.getName ());
    this.dataset = dataset;
  }
}