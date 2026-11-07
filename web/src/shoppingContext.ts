export function advisorContext(products:any[],question:string) {
  const ids=[...new Set(products.map(p=>Number(p.id)).filter(id=>Number.isSafeInteger(id)&&id>0))].slice(0,4);
  sessionStorage.setItem('robotmall-advisor-context',JSON.stringify(ids));
  sessionStorage.setItem('robotmall-advisor-entry',question.slice(0,800));
  return '/advisor';
}
