import { calcularImc, imcPorRegistro } from './imc';

// Índice de masa corporal que se muestra junto a los signos vitales, con los
// puntos de corte para personas mayores (Minsalud 2024 / OPS 2002).
describe('calcularImc', () => {
  it('calcula el IMC con un decimal y lo clasifica', () => {
    expect(calcularImc(64, 160)).toEqual({ valor: 25, categoria: 'Normal', normal: true });
    expect(calcularImc(72, 160)).toEqual({ valor: 28.1, categoria: 'Sobrepeso', normal: false });
    expect(calcularImc(82, 160)).toEqual({ valor: 32, categoria: 'Obesidad', normal: false });
    expect(calcularImc(55, 160)).toEqual({ valor: 21.5, categoria: 'Delgadez', normal: false });
  });

  it('un IMC de 23,0 ya es delgadez en la persona mayor', () => {
    // 58,9 kg y 160 cm → 23,0
    expect(calcularImc(58.9, 160)).toEqual({ valor: 23, categoria: 'Delgadez', normal: false });
    // 59,1 kg y 160 cm → 23,1
    expect(calcularImc(59.1, 160)).toEqual({ valor: 23.1, categoria: 'Normal', normal: true });
  });

  it('sin peso o sin estatura no hay IMC', () => {
    expect(calcularImc(null, 160)).toBeNull();
    expect(calcularImc(64, null)).toBeNull();
    expect(calcularImc(64, 0)).toBeNull();
  });

  it('si un registro no trae estatura usa la más reciente anterior', () => {
    // Del más reciente al más antiguo; solo el más antiguo trae estatura.
    const imc = imcPorRegistro([
      { peso: 70, estatura: null },
      { peso: null, estatura: null },
      { peso: 64, estatura: 160 }
    ]);

    expect(imc.map((i) => i?.valor ?? null)).toEqual([27.3, null, 25]);
  });
});
