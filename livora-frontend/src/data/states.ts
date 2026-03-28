export const STATES_BY_COUNTRY: Record<string, string[]> = {
  US: [
    'Alabama', 'Alaska', 'Arizona', 'Arkansas', 'California', 'Colorado',
    'Connecticut', 'Delaware', 'Florida', 'Georgia', 'Hawaii', 'Idaho',
    'Illinois', 'Indiana', 'Iowa', 'Kansas', 'Kentucky', 'Louisiana',
    'Maine', 'Maryland', 'Massachusetts', 'Michigan', 'Minnesota',
    'Mississippi', 'Missouri', 'Montana', 'Nebraska', 'Nevada',
    'New Hampshire', 'New Jersey', 'New Mexico', 'New York',
    'North Carolina', 'North Dakota', 'Ohio', 'Oklahoma', 'Oregon',
    'Pennsylvania', 'Rhode Island', 'South Carolina', 'South Dakota',
    'Tennessee', 'Texas', 'Utah', 'Vermont', 'Virginia', 'Washington',
    'West Virginia', 'Wisconsin', 'Wyoming', 'District of Columbia',
  ],
  CA: [
    'Alberta', 'British Columbia', 'Manitoba', 'New Brunswick',
    'Newfoundland and Labrador', 'Nova Scotia', 'Ontario',
    'Prince Edward Island', 'Quebec', 'Saskatchewan',
    'Northwest Territories', 'Nunavut', 'Yukon',
  ],
  GB: [
    'England', 'Scotland', 'Wales', 'Northern Ireland',
  ],
  DE: [
    'Baden-Württemberg', 'Bayern', 'Berlin', 'Brandenburg', 'Bremen',
    'Hamburg', 'Hessen', 'Mecklenburg-Vorpommern', 'Niedersachsen',
    'Nordrhein-Westfalen', 'Rheinland-Pfalz', 'Saarland', 'Sachsen',
    'Sachsen-Anhalt', 'Schleswig-Holstein', 'Thüringen',
  ],
  FR: [
    'Île-de-France', 'Provence-Alpes-Côte d\'Azur', 'Auvergne-Rhône-Alpes',
    'Occitanie', 'Nouvelle-Aquitaine', 'Hauts-de-France',
    'Grand Est', 'Pays de la Loire', 'Bretagne', 'Normandie',
    'Bourgogne-Franche-Comté', 'Centre-Val de Loire', 'Corse',
  ],
  NL: [
    'Drenthe', 'Flevoland', 'Friesland', 'Gelderland', 'Groningen',
    'Limburg', 'Noord-Brabant', 'Noord-Holland', 'Overijssel',
    'Utrecht', 'Zeeland', 'Zuid-Holland',
  ],
  ES: [
    'Andalucía', 'Aragón', 'Asturias', 'Baleares', 'Canarias',
    'Cantabria', 'Castilla y León', 'Castilla-La Mancha', 'Cataluña',
    'Extremadura', 'Galicia', 'Madrid', 'Murcia', 'Navarra',
    'País Vasco', 'La Rioja', 'Valencia',
  ],
  IT: [
    'Abruzzo', 'Basilicata', 'Calabria', 'Campania', 'Emilia-Romagna',
    'Friuli Venezia Giulia', 'Lazio', 'Liguria', 'Lombardia', 'Marche',
    'Molise', 'Piemonte', 'Puglia', 'Sardegna', 'Sicilia', 'Toscana',
    'Trentino-Alto Adige', 'Umbria', 'Valle d\'Aosta', 'Veneto',
  ],
  PT: [
    'Aveiro', 'Beja', 'Braga', 'Bragança', 'Castelo Branco', 'Coimbra',
    'Évora', 'Faro', 'Guarda', 'Leiria', 'Lisboa', 'Portalegre',
    'Porto', 'Santarém', 'Setúbal', 'Viana do Castelo',
    'Vila Real', 'Viseu', 'Açores', 'Madeira',
  ],
  BR: [
    'Acre', 'Alagoas', 'Amapá', 'Amazonas', 'Bahia', 'Ceará',
    'Distrito Federal', 'Espírito Santo', 'Goiás', 'Maranhão',
    'Mato Grosso', 'Mato Grosso do Sul', 'Minas Gerais', 'Pará',
    'Paraíba', 'Paraná', 'Pernambuco', 'Piauí', 'Rio de Janeiro',
    'Rio Grande do Norte', 'Rio Grande do Sul', 'Rondônia', 'Roraima',
    'Santa Catarina', 'São Paulo', 'Sergipe', 'Tocantins',
  ],
  AU: [
    'Australian Capital Territory', 'New South Wales', 'Northern Territory',
    'Queensland', 'South Australia', 'Tasmania', 'Victoria',
    'Western Australia',
  ],
  IN: [
    'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar',
    'Chhattisgarh', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh',
    'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra',
    'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab',
    'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura',
    'Uttar Pradesh', 'Uttarakhand', 'West Bengal', 'Delhi',
  ],
  MX: [
    'Aguascalientes', 'Baja California', 'Baja California Sur',
    'Campeche', 'Chiapas', 'Chihuahua', 'Ciudad de México', 'Coahuila',
    'Colima', 'Durango', 'Guanajuato', 'Guerrero', 'Hidalgo', 'Jalisco',
    'México', 'Michoacán', 'Morelos', 'Nayarit', 'Nuevo León', 'Oaxaca',
    'Puebla', 'Querétaro', 'Quintana Roo', 'San Luis Potosí', 'Sinaloa',
    'Sonora', 'Tabasco', 'Tamaulipas', 'Tlaxcala', 'Veracruz',
    'Yucatán', 'Zacatecas',
  ],
  JP: [
    'Hokkaido', 'Aomori', 'Iwate', 'Miyagi', 'Akita', 'Yamagata',
    'Fukushima', 'Ibaraki', 'Tochigi', 'Gunma', 'Saitama', 'Chiba',
    'Tokyo', 'Kanagawa', 'Niigata', 'Toyama', 'Ishikawa', 'Fukui',
    'Yamanashi', 'Nagano', 'Gifu', 'Shizuoka', 'Aichi', 'Mie',
    'Shiga', 'Kyoto', 'Osaka', 'Hyogo', 'Nara', 'Wakayama',
    'Tottori', 'Shimane', 'Okayama', 'Hiroshima', 'Yamaguchi',
    'Tokushima', 'Kagawa', 'Ehime', 'Kochi', 'Fukuoka', 'Saga',
    'Nagasaki', 'Kumamoto', 'Oita', 'Miyazaki', 'Kagoshima', 'Okinawa',
  ],
  RU: [
    'Moscow', 'Saint Petersburg', 'Novosibirsk Oblast',
    'Sverdlovsk Oblast', 'Krasnodar Krai', 'Tatarstan',
    'Chelyabinsk Oblast', 'Nizhny Novgorod Oblast',
    'Samara Oblast', 'Rostov Oblast', 'Bashkortostan',
    'Krasnoyarsk Krai', 'Perm Krai', 'Voronezh Oblast',
  ],
  PL: [
    'Dolnośląskie', 'Kujawsko-Pomorskie', 'Lubelskie', 'Lubuskie',
    'Łódzkie', 'Małopolskie', 'Mazowieckie', 'Opolskie', 'Podkarpackie',
    'Podlaskie', 'Pomorskie', 'Śląskie', 'Świętokrzyskie',
    'Warmińsko-Mazurskie', 'Wielkopolskie', 'Zachodniopomorskie',
  ],
  TR: [
    'Istanbul', 'Ankara', 'Izmir', 'Bursa', 'Antalya', 'Adana',
    'Konya', 'Gaziantep', 'Mersin', 'Kayseri', 'Diyarbakır',
  ],
  SE: [
    'Stockholm', 'Västra Götaland', 'Skåne', 'Östergötland',
    'Uppsala', 'Jönköping', 'Halland', 'Örebro', 'Södermanland',
    'Dalarna', 'Gävleborg', 'Norrbotten', 'Västerbotten',
  ],
  NO: [
    'Oslo', 'Rogaland', 'Vestland', 'Trøndelag', 'Viken',
    'Innlandet', 'Vestfold og Telemark', 'Agder', 'Møre og Romsdal',
    'Nordland', 'Troms og Finnmark',
  ],
  DK: [
    'Hovedstaden', 'Midtjylland', 'Nordjylland', 'Sjælland', 'Syddanmark',
  ],
  FI: [
    'Uusimaa', 'Pirkanmaa', 'Varsinais-Suomi', 'Pohjois-Pohjanmaa',
    'Keski-Suomi', 'Satakunta', 'Pohjanmaa', 'Lappi',
  ],
  BE: [
    'Brussels', 'Antwerp', 'East Flanders', 'West Flanders',
    'Flemish Brabant', 'Limburg', 'Hainaut', 'Liège',
    'Luxembourg', 'Namur', 'Walloon Brabant',
  ],
  AT: [
    'Burgenland', 'Kärnten', 'Niederösterreich', 'Oberösterreich',
    'Salzburg', 'Steiermark', 'Tirol', 'Vorarlberg', 'Wien',
  ],
  CH: [
    'Zürich', 'Bern', 'Luzern', 'Uri', 'Schwyz', 'Obwalden',
    'Nidwalden', 'Glarus', 'Zug', 'Freiburg', 'Solothurn',
    'Basel-Stadt', 'Basel-Landschaft', 'Schaffhausen', 'Appenzell',
    'St. Gallen', 'Graubünden', 'Aargau', 'Thurgau', 'Ticino',
    'Vaud', 'Valais', 'Neuchâtel', 'Genève', 'Jura',
  ],
  CZ: [
    'Prague', 'Central Bohemia', 'South Bohemia', 'Plzeň', 'Karlovy Vary',
    'Ústí nad Labem', 'Liberec', 'Hradec Králové', 'Pardubice',
    'Vysočina', 'South Moravia', 'Olomouc', 'Zlín', 'Moravia-Silesia',
  ],
  RO: [
    'București', 'Cluj', 'Timiș', 'Iași', 'Constanța', 'Brașov',
    'Dolj', 'Galați', 'Prahova', 'Argeș', 'Sibiu', 'Bihor',
  ],
  KR: [
    'Seoul', 'Busan', 'Daegu', 'Incheon', 'Gwangju', 'Daejeon',
    'Ulsan', 'Sejong', 'Gyeonggi', 'Gangwon', 'Chungbuk', 'Chungnam',
    'Jeonbuk', 'Jeonnam', 'Gyeongbuk', 'Gyeongnam', 'Jeju',
  ],
  AR: [
    'Buenos Aires', 'Catamarca', 'Chaco', 'Chubut', 'Córdoba',
    'Corrientes', 'Entre Ríos', 'Formosa', 'Jujuy', 'La Pampa',
    'La Rioja', 'Mendoza', 'Misiones', 'Neuquén', 'Río Negro',
    'Salta', 'San Juan', 'San Luis', 'Santa Cruz', 'Santa Fe',
    'Santiago del Estero', 'Tierra del Fuego', 'Tucumán',
  ],
  CO: [
    'Amazonas', 'Antioquia', 'Arauca', 'Atlántico', 'Bogotá',
    'Bolívar', 'Boyacá', 'Caldas', 'Caquetá', 'Casanare', 'Cauca',
    'Cesar', 'Chocó', 'Córdoba', 'Cundinamarca', 'Guainía',
    'Guaviare', 'Huila', 'La Guajira', 'Magdalena', 'Meta',
    'Nariño', 'Norte de Santander', 'Putumayo', 'Quindío',
    'Risaralda', 'Santander', 'Sucre', 'Tolima', 'Valle del Cauca',
    'Vaupés', 'Vichada',
  ],
  ZA: [
    'Eastern Cape', 'Free State', 'Gauteng', 'KwaZulu-Natal', 'Limpopo',
    'Mpumalanga', 'North West', 'Northern Cape', 'Western Cape',
  ],
};

export function getStatesForCountry(countryCode: string): string[] {
  return STATES_BY_COUNTRY[countryCode] ?? [];
}

export function hasStates(countryCode: string): boolean {
  return countryCode in STATES_BY_COUNTRY;
}
