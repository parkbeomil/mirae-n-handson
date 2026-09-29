// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php
//
// 케이스는 docs/item-bank/BUSINESS-RULES.md 의 규칙(BR-01 ~ BR-24, BR-27)을 겨냥한다.
// 케이스 이름 앞의 [BR-xx] 가 겨냥한 규칙 ID 다.
//
// 규칙
// - 대상 주소는 lib/target.mjs 가 정한다(환경 변수 TARGET_BASE_URL, 없으면 모듈의 레거시 기본 주소).
//   테스트 코드에 주소를 직접 적지 않는다.
// - 응답은 fetchNormalized 로 {status, rows, count, message} 모양으로 바꾼 뒤 스냅샷과 비교한다.
// - 기대값을 손으로 적지 않는다. 지금 시스템의 실제 응답이 기대값이다(npm run baseline -- item-bank).
// - 레거시와 새 API 양쪽에서 같은 결과가 나와야 하므로 건너뛰기를 두지 않는다.
// - 스냅샷은 초기 시드(db/mariadb/init/02-seed.sql) 상태에서 찍는다. register.php 로 문항을 등록하면 결과가 달라진다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

async function search(params) {
  return fetchNormalized(MODULE, PATH, params);
}

describe('item-bank · 문항 검색(search.php)', () => {
  // ---------------------------------------------------------------- 정상 입력
  it('[BR-09 · BR-15 · BR-16 · BR-23] 조건 없음 — 기본 필터 · 기본 정렬 · 첫 페이지', async () => {
    expect(await search({})).toMatchSnapshot();
  });

  it('[BR-01 · BR-07 · BR-08 · BR-10 · BR-15 · BR-19] 단원 M5-1 + 난이도 3 + 제목 오름차순', async () => {
    expect(await search({ unit: 'M5-1', level: '3', sort: 'title', dir: 'asc' })).toMatchSnapshot();
  });

  it('[BR-01 · BR-03 · BR-12 · BR-19 · BR-20 · BR-27] 키워드 분수 + 태그 계산 + 난이도 정렬(방향 생략)', async () => {
    expect(await search({ q: '분수', tag: '계산', sort: 'level' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 경계값
  it('[BR-09 · BR-10] 난이도 5 명시 — 빈 값일 때 빠지는 난이도', async () => {
    expect(await search({ level: '5' })).toMatchSnapshot();
  });

  it('[BR-04] 키워드 100자 — 자르지 않는 최대 길이', async () => {
    expect(await search({ q: '가'.repeat(100) })).toMatchSnapshot();
  });

  it('[BR-04] 키워드 101자 — 자르기 · 경고가 시작되는 길이', async () => {
    expect(await search({ q: '가'.repeat(101) })).toMatchSnapshot();
  });

  it('[BR-03 · BR-06] 키워드 한 글자 — 경고 기준 길이', async () => {
    expect(await search({ q: '분' })).toMatchSnapshot();
  });

  it('[BR-23 · BR-24] 2페이지 — 총 건수가 페이지 크기(20)와 같을 때', async () => {
    expect(await search({ page: '2' })).toMatchSnapshot();
  });

  it('[BR-24] 페이지 0 — 경고 없이 1로 바꾸는 값', async () => {
    expect(await search({ page: '0' })).toMatchSnapshot();
  });

  it('[BR-24] 페이지 1000 — 상한 999를 넘는 값', async () => {
    expect(await search({ page: '1000' })).toMatchSnapshot();
  });

  it('[BR-13 · BR-14] 태그 51자 — 자르기 기준 50자를 넘는 미등록 태그', async () => {
    expect(await search({ tag: '가'.repeat(51) })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 빈 값 · 누락
  it('[BR-09 · BR-16 · BR-18 · BR-24] 모든 파라미터 빈 값 — 파라미터 없음과 같은지', async () => {
    const params = { q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' };
    expect(await search(params)).toMatchSnapshot();
  });

  it('[BR-03 · BR-07] 공백만 있는 키워드 · 단원 — 앞뒤 공백 제거 뒤 빈 값', async () => {
    expect(await search({ q: '   ', unit: ' ' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 이상한 값
  it('[BR-11 · BR-24] 음수 페이지 · 음수 난이도', async () => {
    expect(await search({ page: '-1', level: '-1' })).toMatchSnapshot();
  });

  it('[BR-03 · BR-04] 아주 긴 키워드 5000자', async () => {
    expect(await search({ q: 'a'.repeat(5000) })).toMatchSnapshot();
  });

  it('[BR-07 · BR-08] 형식은 맞지만 없는 단원 코드 Z9-99', async () => {
    expect(await search({ unit: 'Z9-99' })).toMatchSnapshot();
  });

  it('[BR-11] 숫자가 아닌 난이도 abc', async () => {
    expect(await search({ level: 'abc' })).toMatchSnapshot();
  });

  it('[BR-11] 앞자리 0이 붙은 난이도 05', async () => {
    expect(await search({ level: '05' })).toMatchSnapshot();
  });

  it('[BR-02 · BR-05 · BR-07 · BR-17 · BR-18] 잘못된 정렬 · 와일드카드 키워드 · 소문자 단원 · 배열 난이도', async () => {
    const params = { sort: 'DROP', dir: 'up', q: '%', unit: 'm5-1', 'level[]': ['5', '1'] };
    expect(await search(params)).toMatchSnapshot();
  });
});
