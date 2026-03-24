import { randomInt, randomElement } from './random'

const SURNAMES = [
  '陳', '林', '黃', '張', '李', '王', '吳', '劉', '蔡', '楊',
  '許', '鄭', '謝', '洪', '郭', '邱', '曾', '廖', '賴', '徐',
  '周', '葉', '蘇', '莊', '呂', '江', '何', '蕭', '羅', '高',
]

const MALE_GIVEN_NAMES = [
  '志明', '建宏', '俊傑', '建志', '文雄', '志豪', '明哲', '家豪',
  '志偉', '正雄', '國華', '信宏', '冠宇', '柏翰', '承恩',
]

const FEMALE_GIVEN_NAMES = [
  '美玲', '淑芬', '淑惠', '美惠', '雅婷', '麗華', '怡君', '雅芳',
  '淑華', '秀英', '淑娟', '佳蓉', '思穎', '宜蓁', '詩涵',
]

interface CityInfo {
  name: string
  districts: string[]
  areaCode: string
  postalPrefix: string
}

const CITIES: CityInfo[] = [
  { name: '台北市', districts: ['中正區', '大同區', '中山區', '松山區', '大安區', '萬華區', '信義區', '士林區', '北投區', '內湖區', '南港區', '文山區'], areaCode: '02', postalPrefix: '1' },
  { name: '新北市', districts: ['板橋區', '三重區', '中和區', '永和區', '新莊區', '新店區', '土城區', '蘆洲區', '汐止區', '淡水區'], areaCode: '02', postalPrefix: '2' },
  { name: '桃園市', districts: ['桃園區', '中壢區', '大溪區', '楊梅區', '蘆竹區', '龜山區', '八德區', '平鎮區'], areaCode: '03', postalPrefix: '3' },
  { name: '台中市', districts: ['中區', '東區', '南區', '西區', '北區', '北屯區', '西屯區', '南屯區', '大里區', '太平區'], areaCode: '04', postalPrefix: '4' },
  { name: '台南市', districts: ['中西區', '東區', '南區', '北區', '安平區', '安南區', '永康區', '歸仁區', '新化區', '善化區'], areaCode: '06', postalPrefix: '7' },
  { name: '高雄市', districts: ['楠梓區', '左營區', '鼓山區', '三民區', '前金區', '苓雅區', '前鎮區', '小港區', '鳳山區', '大寮區'], areaCode: '07', postalPrefix: '8' },
  { name: '基隆市', districts: ['仁愛區', '信義區', '中正區', '中山區', '安樂區', '暖暖區', '七堵區'], areaCode: '02', postalPrefix: '2' },
  { name: '新竹市', districts: ['東區', '北區', '香山區'], areaCode: '03', postalPrefix: '3' },
  { name: '嘉義市', districts: ['東區', '西區'], areaCode: '05', postalPrefix: '6' },
]

const STREET_NAMES = [
  '中正路', '中山路', '忠孝路', '仁愛路', '信義路', '和平路',
  '民生路', '民族路', '民權路', '光復路', '復興路', '建國路',
  '中華路', '自由路', '文化路', '成功路', '大同路', '三民路',
]

/** NHI ID letter-to-number mapping for first character */
const NHI_LETTER_MAP: Record<string, number> = {
  A: 10, B: 11, C: 12, D: 13, E: 14, F: 15, G: 16, H: 17,
  I: 34, J: 18, K: 19, L: 20, M: 21, N: 22, O: 35, P: 23,
  Q: 24, R: 25, S: 26, T: 27, U: 28, V: 29, W: 32, X: 30,
  Y: 31, Z: 33,
}

const NHI_LETTERS = Object.keys(NHI_LETTER_MAP)

/** Generate a valid Taiwan NHI ID (ROC national ID format) */
export function generateNhiId(gender: 'male' | 'female'): string {
  const letter = randomElement(NHI_LETTERS)
  const genderDigit = gender === 'male' ? 1 : 2
  const digits = Array.from({ length: 7 }, () => randomInt(0, 9))

  // Compute check digit
  const n = NHI_LETTER_MAP[letter]
  const d1 = Math.floor(n / 10)
  const d2 = n % 10
  const weights = [1, 9, 8, 7, 6, 5, 4, 3, 2]
  const allDigits = [d1, d2, genderDigit, ...digits]
  let sum = 0
  for (let i = 0; i < weights.length; i++) {
    sum += allDigits[i] * weights[i]
  }
  const checkDigit = (10 - (sum % 10)) % 10

  return `${letter}${genderDigit}${digits.join('')}${checkDigit}`
}

/** Generate a Taiwanese patient name */
export function generateName(gender: 'male' | 'female'): { family: string; given: string } {
  const family = randomElement(SURNAMES)
  const given = randomElement(gender === 'male' ? MALE_GIVEN_NAMES : FEMALE_GIVEN_NAMES)
  return { family, given }
}

/** Generate a Taiwan address */
export function generateAddress(): {
  city: string
  district: string
  postalCode: string
  line: string
} {
  const city = randomElement(CITIES)
  const district = randomElement(city.districts)
  const street = randomElement(STREET_NAMES)
  const num = randomInt(1, 300)
  const postalCode = `${city.postalPrefix}${randomInt(10, 99)}`
  return {
    city: city.name,
    district,
    postalCode,
    line: `${street}${num}號`,
  }
}

/** Generate a Taiwan mobile phone number */
export function generateMobile(): string {
  const prefix = `09${randomInt(10, 99)}`
  const suffix = `${randomInt(100, 999)}${randomInt(100, 999)}`
  return `${prefix}-${suffix.slice(0, 3)}-${suffix.slice(3)}`
}

/** Generate a Taiwan landline phone number */
export function generateLandline(): string {
  const city = randomElement(CITIES)
  const num = `${randomInt(1000, 9999)}-${randomInt(1000, 9999)}`
  return `${city.areaCode}-${num}`
}

/** Generate a random birth date (age 18–80) */
export function generateBirthDate(): string {
  const now = new Date()
  const age = randomInt(18, 80)
  const year = now.getFullYear() - age
  const month = randomInt(1, 12)
  const day = randomInt(1, 28) // safe for all months
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

/** Generate a random date within a given range */
export function randomDateInRange(from?: string, to?: string, defaultDaysBack = 365): string {
  const now = new Date()
  const end = to ? new Date(to) : now
  const start = from ? new Date(from) : new Date(end.getTime() - defaultDaysBack * 86400000)
  const diff = end.getTime() - start.getTime()
  const randomMs = Math.floor(Math.random() * Math.max(diff, 1))
  const date = new Date(start.getTime() + randomMs)
  return date.toISOString().split('T')[0]
}

/** Generate a random gender */
export function randomGender(): 'male' | 'female' {
  return Math.random() < 0.5 ? 'male' : 'female'
}

/** Generate a fake email from a name */
export function generateEmail(family: string, given: string): string {
  const providers = ['gmail.com', 'yahoo.com.tw', 'hotmail.com', 'outlook.com']
  const num = randomInt(1, 999)
  return `${given.toLowerCase()}${family.toLowerCase()}${num}@${randomElement(providers)}`
}
