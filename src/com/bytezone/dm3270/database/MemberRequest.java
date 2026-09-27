package com.bytezone.dm3270.database;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.Member;

import java.util.List;

// -----------------------------------------------------------------------------------//
public class MemberRequest extends DatabaseRequest
// -----------------------------------------------------------------------------------//
{
  /*
   * Os nomes e o dataset nunca mudam depois do construtor. O dataset so e guardado na
   * requisicao por nome; na por objeto ele fica nulo, e o dataset e o do proprio membro. O
   * membro e a lista sao a resposta do worker.
   */
  private Member member;
  private final String memberName;
  private final Dataset dataset;
  private final String datasetName;
  private List<Member> members;

  // ---------------------------------------------------------------------------------//
  public MemberRequest (Initiator initiator, Command command, Member member)
  // ---------------------------------------------------------------------------------//
  {
    super (initiator, command);
    this.member = member;
    this.dataset = null;
    this.datasetName = member.getDataset ().getName ();
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
  public Member getMember ()
  // ---------------------------------------------------------------------------------//
  {
    return member;
  }

  // ---------------------------------------------------------------------------------//
  void setMember (Member member)
  // ---------------------------------------------------------------------------------//
  {
    this.member = member;
  }

  // ---------------------------------------------------------------------------------//
  public String getMemberName ()
  // ---------------------------------------------------------------------------------//
  {
    return memberName;
  }

  // ---------------------------------------------------------------------------------//
  public Dataset getDataset ()
  // ---------------------------------------------------------------------------------//
  {
    return dataset;
  }

  // ---------------------------------------------------------------------------------//
  public String getDatasetName ()
  // ---------------------------------------------------------------------------------//
  {
    return datasetName;
  }

  // ---------------------------------------------------------------------------------//
  public List<Member> getMembers ()
  // ---------------------------------------------------------------------------------//
  {
    return members;
  }

  // ---------------------------------------------------------------------------------//
  void setMembers (List<Member> members)
  // ---------------------------------------------------------------------------------//
  {
    this.members = members;
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
