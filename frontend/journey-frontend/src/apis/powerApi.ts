import type { Power } from "../models/Power";

export async function getDemoPowers(): Promise<Power[]> {
  return [
    {
      id: "cloud-kill-demo",
      name: "Cloud Kill",
      lvl: 3,
      type: "Destruction",
      tpCost: 5,
      hpCost: null,
      mpCost: 41,
      epCost: null,
      materialComponents: "None",
      verbal: true,
      sight: true,
      somatic: true,
      isDistinct: false,
      concentration: false,
      range: "50ft",
      area: "20ft",
      duration: "12s",
      effect:
        "Choose a point within range, all targets in the area must make a contested Resilience skill roll against your Destruction Magic skill roll. On a failure those targets take 3d8 + MD poison damage and take 2d6 Poison on the start of the next round. On a straight success, targets take half damage. On the start of the round you can move the cloud up to half its range. When a target first comes into contact with the power they suffer the effects of the power. If a target dies within the round they suffer the effects of this power all targets within half of the area from that target must make the contested skill roll and suffer the effects of this power on a failure.",
      empowerEffect:
        "For an additional 10MP you can increase the range by 10ft and area by 5ft.",
      lvlUpEffect: "Poison damage increases by 1d8.",
      categories: ["DAMAGE", "POISON", "CONTROL", "MAGIC"],
      contentSourceName: "Example",
    },
    {
      id: "arcane-burst-demo",
      name: "Arcane Burst",
      lvl: 2,
      type: "Destruction",
      tpCost: 3,
      hpCost: null,
      mpCost: 18,
      epCost: null,
      materialComponents: "Crystal shard",
      verbal: true,
      sight: false,
      somatic: true,
      isDistinct: false,
      concentration: false,
      range: "30ft",
      area: "10ft",
      duration: "Instant",
      effect:
        "A sudden wave of magical force erupts from your hands and strikes all creatures in the target area. Each target takes 2d6 + MD force damage.",
      empowerEffect: "Increase the range by 10ft and damage by 1d6.",
      lvlUpEffect: "Damage increases by +1d6 at higher levels.",
      categories: ["DAMAGE", "FORCE", "LIGHTNING"],
      contentSourceName: "Example",
    },
  ];
}
