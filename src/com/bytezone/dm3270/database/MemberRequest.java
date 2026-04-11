package com.bytezone.dm3270.database;

import java.util.List;

// -----------------------------------------------------------------------------------//
public class MemberRequest extends DatabaseRequest
// -----------------------------------------------------------------------------------//
{
  public Member member;
  public String memberName;
  public Dataset dataset;
  public String datasetName;
  public List<Member> members;

  // ---------------------------------------------------------------------------------//
  public MemberRequest (Initiator initiator, Command command, Member member)
  // ---------------------------------------------------------------------------------//
  {
    super (initiator, command);
    this.member = member;
    this.datasetName = member.dataset.getName ();
    this.memberName = member.getName ();
  }

  // ---------------------------------------------------------------------------------//
  public MemberRequest (Initiator initiator, Command command, Dataset dataset,
      String memberName)
  // ---------------------------------------------------------------------------------//
  {
    super (initiator, command);
    this.dataset = dataset;
    this.datasetName = dataset.getName ();
    this.memberName = memberName;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();

    text.append (super.toString ());
    text.append (String.format ("Dataset ....... %s%n", datasetName));
    text.append (String.format ("Member ........ %s%n", memberName));

    return text.toString ();
  }
}