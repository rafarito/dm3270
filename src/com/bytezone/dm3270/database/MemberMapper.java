package com.bytezone.dm3270.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/*
 * A traducao entre um Member e uma linha da tabela MEMBERS. Mesma historia do DatasetMapper,
 * com dez parametros em vez de quinze, cobertos por
 * DatabaseTest.memberColumnsAreBoundInOrder.
 */
// -----------------------------------------------------------------------------------//
final class MemberMapper
// -----------------------------------------------------------------------------------//
{
  private MemberMapper ()
  {
  }

  // ---------------------------------------------------------------------------------//
  static void bind (PreparedStatement ps, Member member) throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    ps.setString (1, member.getId ());
    ps.setInt (2, member.getSize ());
    ps.setInt (3, member.getInit ());
    ps.setInt (4, member.getMod ());
    ps.setInt (5, member.getVv ());
    ps.setInt (6, member.getMm ());
    ps.setDate (7, member.getCreatedSQL () == null ? null : member.getCreatedSQL ());
    ps.setDate (8, member.getChangedSQL () == null ? null : member.getChangedSQL ());
    ps.setString (9, member.getDataset ().getName ());
    ps.setString (10, member.getName ());
  }

  // ---------------------------------------------------------------------------------//
  static Member read (ResultSet rs, Dataset dataset) throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    Member member = new Member (dataset, rs.getString ("name"));

    member.setID (rs.getString ("id"));
    member.setSize (rs.getInt ("size"), rs.getInt ("init"), rs.getInt ("mod"),
        rs.getInt ("vv"), rs.getInt ("mm"));
    member.setDates (rs.getDate ("created"), rs.getDate ("changed"));

    return member;
  }
}
