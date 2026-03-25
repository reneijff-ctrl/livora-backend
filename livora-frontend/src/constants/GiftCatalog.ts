export interface Gift {
  id: string;
  name: string;
  price: number;
  animationType: string;
  rarity: "common" | "rare" | "epic" | "legendary";
  soundProfile: "common" | "rare" | "epic" | "legendary";
}

export const GIFT_CATALOG: Gift[] = [
  {
    id: "rose",
    name: "Rose",
    price: 5,
    animationType: "floatHearts",
    rarity: "common",
    soundProfile: "common"
  },
  {
    id: "champagne",
    name: "Champagne",
    price: 20,
    animationType: "golden-coin-burst",
    rarity: "rare",
    soundProfile: "rare"
  },
  {
    id: "fireworks",
    name: "Fireworks",
    price: 50,
    animationType: "fireworks",
    rarity: "epic",
    soundProfile: "epic"
  },
  {
    id: "lambo",
    name: "Lambo",
    price: 100,
    animationType: "goldenRain",
    rarity: "epic",
    soundProfile: "epic"
  },
  {
    id: "meteor",
    name: "Meteor",
    price: 500,
    animationType: "meteorFall",
    rarity: "legendary",
    soundProfile: "legendary"
  },
  {
    id: "galaxyStorm",
    name: "Galaxy Storm",
    price: 1000,
    animationType: "cosmicStorm",
    rarity: "legendary",
    soundProfile: "legendary"
  }
];
