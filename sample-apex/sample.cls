public class EnforcePrivateGetter {
    @AuraEnabled
    public Integer counter { private get; set; }

    @AuraEnabled
    public static EnforcePrivateGetter getRepro1()
    {
        EnforcePrivateGetter result = new EnforcePrivateGetter();
        result.counter = 2; 
        return result;
    }

    @AuraEnabled
    public static PagedResult getRepro2()
    {
        PagedResult result = new PagedResult();
        result.records = [...];
        return result;
    }

    private class PagedResult {
        @AuraEnabled
        public Object[] records { protected get; set; }
    }
}
