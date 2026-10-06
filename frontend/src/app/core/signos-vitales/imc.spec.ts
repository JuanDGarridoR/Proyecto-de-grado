import { calcularImc, imcPorRegistro } from './imc';

// Índice de masa corporal que se muestra junto a los signos vitales.
describe('calcularImc', () => {
  it('calcula el IMC con un decimal y lo clasifica', () => {
    expect(calcularImc(64, 160)).toEqual({ valor: 25, categoria: 'Sobrepeso', normal: false });
    expect(calcularImc(55, 160)).toEqual({ valor: 21.5, categoria: 'Peso normal', normal: true });
    expect(calcularImc(45, 160)).toEqual({ valor: 17.6, categoria: 'Bajo peso', normal: false });
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
